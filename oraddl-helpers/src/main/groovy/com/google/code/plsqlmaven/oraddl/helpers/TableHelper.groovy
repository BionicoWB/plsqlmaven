package com.google.code.plsqlmaven.oraddl.helpers

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import groovy.sql.Sql
import java.sql.SQLException

class TableHelper extends OraDdlHelper
{
      private static tempColIndex= 99999999999999999999999999;
      
      public TableHelper(sql,log,username)
      {
          super(sql,log,username);
      }

      public boolean extract(name,xml)
      {
          if (name.toUpperCase()==~'JAVA\\$.*')
            return false
            
          def constraints= getConstraints(name)
          
          xml.table('name': xid(name))
          {
              xml.columns()
              {
                  sql.eachRow("select * from user_tab_columns a where table_name = upper(${name}) order by column_id")
                  {
                     def col= it.toRowResult()
                     def p= col.data_type.indexOf('(')
                     
                     xml.column('name':          xid(col.column_name),
                                'type':          col.data_type.toLowerCase().substring(0,(p==-1?col.data_type.length():p)),
                                'precision':     col.data_precision,
                                'scale':         col.data_scale,
                                'length':        rd(col.char_length,0),
                                'default':       (col.data_default?.trim()?.toLowerCase()=='null' ? null : col.data_default?.trim()),
                                'primary':       simpleKey(col.column_name,constraints, 'P'),
                                'unique':        simpleKey(col.column_name,constraints, 'U'),
                                'check':         simpleCheck(col.column_name,constraints),
                                'not-null':      simpleNotNull(col.column_name,constraints),
                                'references':    simpleForeignKey(col.column_name,constraints))
                  }
              }
              
              if (constraints.size()>0)
                  xml.constraints()
                  {
                      constraints.each
                      {
                          constraint ->
                          
                          xml.constraint('name': xid(constraint.constraint_name),
                                         'type': constraintType(constraint.constraint_type),
                                         'expression': constraint.search_condition,
                                         'on-delete': (constraint.delete_rule!='NO ACTION' ? constraint.delete_rule?.toLowerCase() : null))
                          {
                              if (constraint.constraint_type!='C')
                              xml.columns()
                              {
                                  constraint.columns.each
                                  {
                                     column ->
                                     
                                     xml.column('name': xid(column.column_name))
                                  }
                              }
                              
                              if (constraint.constraint_type=='R')
                              {
                                  
                                  xml.references('table': xid(constraint.rcolumns[0].table_name),
                                                 'owner': constraint.rcolumns[0].owner==username.toUpperCase() ? null : xid(constraint.rcolumns[0].owner))
                                  {
                                      constraint.rcolumns.each
                                      {
                                         column ->
                                         
                                         xml.column('name': xid(column.column_name))
                                      }
                                  }
                              }
                              
                          }
                      }
                  }
              
          }
          
          return true;
      }
      
      private getConstraints(tableName)
      {
          def constraints= []
          
          sql.eachRow("select * from user_constraints a where table_name = upper(${tableName}) order by constraint_type")
          {
             def cons= it.toRowResult()
             cons['columns']= []
             
             sql.eachRow("select * from user_cons_columns where constraint_name= ${cons.constraint_name} order by position")
             {
                cons.columns << it.toRowResult()
             }
             
             if (cons.constraint_type=='R')
             {
                 cons['rcolumns']= []
                 
                 sql.eachRow("select * from all_cons_columns where constraint_name= ${cons.r_constraint_name} and owner= ${cons.r_owner} order by position")
                 {
                     def rcol= it.toRowResult()
                     cons.rcolumns << rcol
                 }
             }
             
             constraints << cons
          }
          
          return constraints 
      }
      
      private constraintType(type)
      {
          switch (type)
          {
              case 'P':
               return 'primary'
              case 'U':
               return 'unique'
              case 'C':
               return 'check'
              case 'R':
               return 'foreign'
          }
      }
      
      private simpleKey(columnName,constraints,type)
      {
          def constraint= constraints.find{ constraint -> (constraint.constraint_type==type
                                   &&constraint.generated.startsWith('GENERATED')
                                   &&constraint.columns.size()==1
                                   &&constraint.columns.find{ column -> (column.column_name==columnName) }) } 
          
          constraints.remove constraint
          
          return constraint ? 'true' : null
      }
      
      private simpleForeignKey(columnName,constraints)
      {
          def fk= constraints.find{ constraint -> (constraint.constraint_type=='R'
                                           &&constraint.generated.startsWith('GENERATED')
                                           &&constraint.columns.size()==1
                                           &&constraint.columns.find{ column -> (column.column_name==columnName) }) }
          
          if (fk)
          {          
             constraints.remove fk
             def rcol= fk.rcolumns[0]             
             return ((username.toUpperCase()!=rcol.owner ? xid(rcol.owner)+'.' : '')+xid(rcol.table_name)+(rcol.column_name!= columnName ? '.'+xid(rcol.column_name) : ''))
          }
          
          return null
      }

      private simpleCheck(columnName,constraints)
      {
          def checks= constraints.findAll{ constraint -> (constraint.constraint_type=='C'
                                                     && constraint.columns.find{ column -> (column.column_name==columnName) }
                                                     && constraint.columns.size()==1
                                                     && constraint.generated.startsWith('GENERATED') 
                                                     && !isNotNullSearchCondition(constraint.search_condition,columnName)) }
          
          def r
          
          if (checks)
          {
              constraints.removeAll checks
              r= checks?.collect{ constraint -> constraint.search_condition }?.join(' and ')
          }
                                                    
          return r ? r : null                                                     
      }
      
      private isNotNullSearchCondition(dbSearchCondition,columnName)
      {
          return cmp(dbSearchCondition.toLowerCase().replaceAll('"',''),(columnName+' IS NOT NULL').toLowerCase())
      }
      
      private simpleNotNull(columnName,constraints)
      {
          def colInPk= (boolean)constraints.find{ constraint -> (constraint.constraint_type=='P'
                                                                 &&constraint.columns.find{ column -> (column.column_name==columnName) }) }
          
          def constraint= constraints.find{ constraint -> (constraint.constraint_type=='C'
                                                 && constraint.columns.find{ column -> (column.column_name==columnName) }
                                                 && constraint.columns.size()==1
                                                 && constraint.generated.startsWith('GENERATED') 
                                                 && isNotNullSearchCondition(constraint.search_condition,columnName)) } 
          
          if (constraint)
          {
            constraints.remove constraint
            return colInPk ? null : 'true'
          }
          else
            return null
           
      }
      
      public boolean exists(table)
      {
           def exists= false;
           sql.eachRow("select 1 from user_tables where table_name= upper(${oid(table.'@name',false)})")
           { exists= true }
           
           return exists
      }
      
      public create(table)
      {
          def changes= []
          
          changes << [
                              type: 'create_table',
                               ddl: 'create table '+oid(table.'@name')+'('+table.columns.column.collect{ col-> oid(col.'@name')+' '+getColumnType(col) }.join(',')+')',
                       privMessage: "You need to: grant create table to ${username}"         
                     ]
          
          table.columns.column.each
          {
              column ->
              
              if (column.'@primary'=='true')
                changes << add_simple_primary(table,column)

              if (column.'@not-null'=='true')
                changes << add_simple_notnull(table,column)

              if (column.'@unique'=='true')
                changes << add_simple_unique(table,column)
                
              if (column.'@references')
                changes << add_simple_foreign(table,column)

          }
          
          table.constraints.constraint.each
          {
              constraint ->
              
              changes << add_constraint(table,constraint)
          }
          
          return changes
      }
      
      public detectChanges(source,target)
      {
          def changes= [];
          
          target.columns.column.each
          {
                targetCol ->
                
                def sourceCol= source.columns.column.find({ col -> col.'@name'== targetCol.'@name' })
                
                if (!sourceCol)
                    changes << add_column(target,targetCol)
                else
                {
                    if (  !cmp(sourceCol,targetCol,'type')
                        ||!cmp(sourceCol,targetCol,'precision')
                        ||!cmp(sourceCol,targetCol,'scale')
                        ||!cmp(sourceCol,targetCol,'length')
                        ||!cmp(sourceCol,targetCol,'default'))
                      changes += modify_column(target,targetCol,sourceCol)
                    
                    if (!cmp(sourceCol,targetCol,'primary','false'))
                      changes += modify_simple_primary(target,targetCol)

                    if (!cmp(sourceCol,targetCol,'unique','false'))
                      changes += modify_simple_unique(target,targetCol)

                    if (!cmp(sourceCol,targetCol,'check'))
                      changes += modify_simple_check(target,targetCol)
                      
                    if (!cmp(sourceCol,targetCol,'not-null','false'))
                      changes += modify_simple_notnull(target,targetCol)

                    if (!cmp(sourceCol,targetCol,'references'))
                      changes += modify_simple_foreign(target,targetCol)
                }
          }

		  def renamedConstraints= []
          
          source.constraints.constraint.findAll{ c -> (!(c.'@name' in target.constraints.constraint*.'@name')) }.each
          {
                constraint ->

				def rename= false;
				def targetCons;
				
				if (constraint.'@type'=='primary') 
				{
					targetCons= target.constraints.constraint.find{ c -> (c.'@type'=='primary') }
					
					try
					{
						if (constraint.columns.column.size()!=targetCons.columns.column.size())
	    					    throw new ContextException('column count differs')
					    else
							targetCons.columns.column.eachWithIndex
							{
								targetCol, index ->
								
								def sourceCol= constraint.columns.column[index];
								
								if (!cmp(sourceCol,targetCol,'name'))
								  throw new ContextException('columns differs')
							}
							
					    rename= true;
					}
					catch (ContextException cex)
					{}
			    }
				
				if (rename)
				{
				  renamedConstraints << targetCons.'@name'
                  changes << rename_constraint(target,constraint,targetCons);
				}
				else                
                  changes << drop_constraint(target,constraint);
          }
              
          source.columns.column.findAll{ col -> (!(col.'@name' in target.columns.column*.'@name')) }.each
          {
              column ->
              
              changes << drop_column(target,column)
          }
		  
          target.constraints.constraint.each
          {
                 targetCons ->

                 def sourceCons= source.constraints.constraint.find{ c-> (c.'@name'==targetCons.'@name')}
                 
                 if (!sourceCons)
				 {
					 if (!targetCons.'@name' in renamedConstraints)
                       changes << add_constraint(target,targetCons)
				 }
                 else
                 {
                     try
                     {
                        if (  !cmp(sourceCons,targetCons,'type')
                            ||!cmp(sourceCons,targetCons,'on-delete','no action')
                            ||!cmp(sourceCons,targetCons,'expression')
                            ||!cmp(sourceCons.references[0]?.'@owner',targetCons.references[0]?.'@owner')
                            ||!cmp(sourceCons.references[0]?.'@table',targetCons.references[0]?.'@table'))
                            throw new ContextException('base metadata differs')
                        else
                        if (targetCons.'@type'!='check')
                        {
                            if (sourceCons.columns.column.size()!=targetCons.columns.column.size())
                                throw new ContextException('column count differs')
                            else      
                                targetCons.columns.column.eachWithIndex
                                {
                                    targetCol, index -> 
                                    
                                    def sourceCol= sourceCons.columns.column[index];
                                    
                                    if (!cmp(sourceCol,targetCol,'name'))
                                      throw new ContextException('columns differs')
                                }
                              
                            if (targetCons.'@type'=='foreign')
                            {
                                if (sourceCons.references.column.size()!=targetCons.references.column.size())
                                    throw new ContextException('referenced column count differs')
                                else
                                    targetCons.references.column.eachWithIndex
                                    {
                                        targetCol, index ->
                                        
                                        def sourceCol= sourceCons.references.column[index];
                                        
                                        if (!cmp(sourceCol,targetCol,'name'))
                                          throw new ContextException('referenced columns differs')
                                    }
                             }
                        }
                     }
                     catch (ContextException ex)
                     {
                         changes += modify_constraint(target,targetCons,ex.context)
                     }
                  }
                     
          }
          
          return changes
      }
   
      
      /*   CHANGES    */
      
      public drop_column(table,column)
      {
           return [
                            type: 'drop_column',
                             ddl: "alter table ${oid(table.'@name')} drop column ${oid(column.'@name')}",
                     privMessage: "You need to: grant alter table to ${username}"
                  ]
      }
   
      public modify_column(table,targetCol,sourceCol)
      {
          def changes= []
          
          if (targetCol.'@type'=='varchar2'&&sourceCol.'@type'=='clob')
          {
              changes+= column_clob_to_varchar2(table,targetCol);
          }
          else
          if (targetCol.'@type'=='varchar2'&&sourceCol.'@type'=='number')
          {
              changes+= column_number_to_varchar2(table,targetCol);
          }
          else
          if (targetCol.'@type'=='number'&&sourceCol.'@type'=='number'&&
              ((!cmp(sourceCol,targetCol,'precision')
                  &&(!(dv(targetCol.'@precision'.toInteger(),9999999)>dv(sourceCol.'@precision'.toInteger(),9999999))))
              ||!cmp(sourceCol,targetCol,'scale',0)))
          {
              changes+= column_number_reduce_precision(table,targetCol);
          }
          else
          {
             def columnType= getColumnType(targetCol)
             
             changes+= [
                                 type: 'modify_column',
                                  ddl: "alter table ${oid(table.'@name')} modify (${oid(targetCol.'@name')} ${columnType})",
                          privMessage: "You need to: grant alter table to ${username}"
                       ]
          }
          
          return changes
      }
   
      public add_column(table,column)
      {
          def columnType= getColumnType(column)
          
          return [
                                 type: 'add_column',
                                  ddl: "alter table ${oid(table.'@name')} add (${oid(column.'@name')} ${columnType})",
                          privMessage: "You need to: grant alter table to ${username}"
                 ]
          
      }
      
      public add_constraint(table,constraint)
      {
          def ddl= "alter table ${oid(table.'@name')} add constraint ${oid(constraint.'@name')} ";
          
          if (constraint.'@type'=='primary')
              ddl+="primary key ("+constraint.columns.column*.'@name'.join(',')+")"
          else
          if (constraint.'@type'=='foreign')
          {
              ddl+="foreign key ("+constraint.columns.column*.'@name'.join(',')+")"
              def owner= constraint.references[0].'@owner'
              ddl+=" references "+( owner&&owner!=username ? owner+'.' : '' )+constraint.references[0].'@table'
              ddl+='('+constraint.references.column*.'@name'.join(',')+')'
              
              if (dv(constraint.'@on-delete','no action')!='no action')
                ddl+=' on delete '+constraint.'@on-delete'
          }
          else
          if (constraint.'@type'=='check')
              ddl+="check ("+constraint.'@expression'+")"
          else
          if (constraint.'@type'=='unique')
              ddl+="unique ("+constraint.columns.column*.'@name'.join(',')+")"

          
          return [
                         type: 'add_constraint',
                          ddl: ddl,
                  privMessage: "You need to: grant alter table to ${username}",
				   constraint: [type: constraint.'@type']
                 ]

      }
   
      public rename_constraint(table,sourceCons,targetCons)
      {
          return [
                              type: 'rename_constraint',
                               ddl: "alter table ${oid(table.'@name')} rename constraint ${oid(sourceCons.'@name')} to ${oid(targetCons.'@name')}",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
      }

	  public drop_constraint(table,constraint)
      {
          return [
                              type: 'drop_constraint',
                               ddl: "alter table ${oid(table.'@name')} drop constraint ${oid(constraint.'@name')} cascade",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
      }
      
      public modify_constraint(table,constraint,cause=null)
      {
          return [drop_constraint(table,constraint),
                  add_constraint(table,constraint)]
      }
      
      public add_simple_primary(table,column)
      {
          return [
                              type: 'add_constraint',
                               ddl: "alter table ${oid(table.'@name')} add primary key (${oid(column.'@name')})",
                       privMessage: "You need to: grant alter table to ${username}",
				        constraint: [type: 'primary']
                 ]
      }

      public drop_simple_primary(table,column)
      {
          return [
                              type: 'drop_constraint',
                               ddl: "alter table ${oid(table.'@name')} drop primary key cascade",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
      }
      
      public modify_simple_primary(table,column)
      {
          return [drop_simple_primary(table,column),
                  add_simple_primary(table,column)]
      }

      public add_simple_unique(table,column)
      {
          return [
                              type: 'add_constraint',
                               ddl: "alter table ${oid(table.'@name')} add unique (${oid(column.'@name')})",
                       privMessage: "You need to: grant alter table to ${username}",
				        constraint: [type: 'unique']
                 ]
      }

      public drop_simple_unique(table,column)
      {
          return [
                              type: 'drop_constraint',
                               ddl: """declare
                                         v_constraint  varchar2(30);
                                       begin
                                         
                                         select a.constraint_name
                                           into v_constraint
                                           from user_constraints a,
                                                user_cons_columns b
                                          where a.constraint_name= b.constraint_name
                                            and a.table_name= b.table_name
                                            and a.table_name= upper('${oid(table.'@name',false)}')
                                            and b.column_name= upper('${oid(column.'@name',false)}')
                                            and a.generated= 'GENERATED NAME'
                                            and a.constraint_type= 'U'
                                            and rownum < 2;
                                         
                                         execute immediate 'alter table ${oid(table.'@name')} drop constraint "'||v_constraint||'";
                                         
                                       end;""",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
      }
      
      public modify_simple_unique(table,column)
      {
          return [drop_simple_unique(table,column),
                  add_simple_unique(table,column)]
      }

      public add_simple_check(table,column)
      {
          return [
                              type: 'add_constraint',
                               ddl: "alter table ${oid(table.'@name')} add check (${oid(column.'@name')})",
                       privMessage: "You need to: grant alter table to ${username}",
				        constraint: [type: 'check']
                 ]
      }

      public drop_simple_check(table,column)
      {
          return [
                              type: 'drop_constraint',
                               ddl: """declare
                                         v_constraint  varchar2(30);
                                       begin
                                         
                                           select a.constraint_name
                                             into v_constraint
                                             from user_constraints a,
                                                  user_cons_columns b
                                            where a.constraint_name= b.constraint_name 
                                              and b.column_name= upper('${oid(column.'@name',false)}')
                                              and a.table_name= b.table_name
                                              and a.table_name= upper('${oid(table.'@name',false)}')
                                              and a.generated= 'GENERATED NAME'
                                              and a.constraint_type= 'C'
                                              and (select count(*) 
                                                     from user_cons_columns
                                                    where constraint_name= a.constraint_name)= 1;
                                         
                                         execute immediate 'alter table ${oid(table.'@name')} drop constraint "'||v_constraint||'"';
                                         
                                       end;""",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
      }

      public modify_simple_check(table,column)
      {
          return [drop_simple_check(table,column),
                  add_simple_check(table,column)]
          
      }

      public add_simple_notnull(table,column)
      {
          return [
                              type: 'add_constraint',
                               ddl: "alter table ${oid(table.'@name')} add check (${oid(column.'@name')} is not null)",
                       privMessage: "You need to: grant alter table to ${username}",
				        constraint: [type: 'not-null']
                 ]
      }

      public drop_simple_notnull(table,column)
      {
          return [
                              type: 'drop_constraint',
                               ddl: """declare
                                         v_constraint  varchar2(30);
                                       begin
                                         
                                             for c_cur in (select a.constraint_name,
                                                                  a.search_condition,
                                                                  b.column_name
                                                             from user_constraints a,
                                                                  user_cons_columns b
                                                            where a.constraint_name= b.constraint_name 
                                                              and b.column_name= upper('${oid(column.'@name',false)}')
                                                              and a.table_name= b.table_name
                                                              and a.table_name= upper('${oid(table.'@name',false)}')
                                                              and a.generated= 'GENERATED NAME'
                                                              and a.constraint_type= 'C'
                                                              and (select count(*) 
                                                                     from user_cons_columns
                                                                    where constraint_name= a.constraint_name)= 1)
                                             loop
                                              if c_cur.search_condition= '"'||c_cur.column_name||'" IS NOT NULL' then
                                                execute immediate 'alter table ${oid(table.'@name')} drop constraint "'||c_cur.constraint_name||'"';
                                              end if;
                                             end loop;
                                         
                                       end;""",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
      }
      
      public modify_simple_notnull(table,column)
      {
          return [drop_simple_notnull(table,column),
                  add_simple_notnull(table,column)]
      }

      public add_simple_foreign(table,column)
      {
          def ref= column.'@references'.split('\\.')
          def references;
          
          if (ref.length==3)
            references= oid(ref[0])+'.'+oid(ref[1])+'('+oid(ref[2])+')'
          else
          if (ref.length==2)
            references= oid(ref[0])+'('+oid(ref[1])+')'
          else
            references= oid(ref[0])+'('+oid(column.'@name')+')'
  
          return [
                              type: 'add_constraint',
                               ddl: "alter table ${oid(table.'@name')} add foreign key (${oid(column.'@name')}) references ${references}",
                       privMessage: "You need to: grant alter table to ${username}",
					    constraint: [type: 'foreign']
                 ]
      }

      public drop_simple_foreign(table,column)
      {
          return [
                              type: 'drop_constraint',
                               ddl: """declare
                                         v_constraint  varchar2(30);
                                       begin
                                         
                                           select a.constraint_name
                                             into v_constraint
                                             from user_constraints a,
                                                  user_cons_columns b
                                            where a.constraint_name= b.constraint_name
                                              and a.table_name= b.table_name
                                              and a.table_name= upper('${oid(table.'@name',false)}')
                                              and b.column_name= upper('${oid(column.'@name',false)}')
                                              and a.generated= 'GENERATED NAME'
                                              and a.constraint_type= 'R';
                                                                                                                           
                                         execute immediate 'alter table ${oid(table.'@name')} drop constraint "'||v_constraint||'"';
                                         
                                       end;""",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
      }

      public modify_simple_foreign(table,column)
      {
          return [drop_simple_foreign(table,column),
                  add_simple_foreign(table,column)]
      }
      
      public column_clob_to_varchar2(table,column)
      {
          def changes= [];
          def tempIndex= tempColIndex--;
          def columnType= getColumnType(column);
          
          try
          {
             changes << [
                             type: 'drop_maven_temporary_column',
                         mainType: 'modify_column',
                        tempIndex: tempIndex,
                              ddl: "alter table ${oid(table.'@name')} drop column mvn_${tempIndex}",
                         failSafe: true
                        ]
          }
          catch (Exception ex)
          {}
          
          changes << [
                              type: 'add_maven_temporary_column',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} add (mvn_${tempIndex} ${columnType})",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]
          
          def target_length= column.'@length';

          changes << [
                              type: 'maven_translate_values_clob_to_varchar2',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: """declare
                                          v_value ${columnType};
                                          
                                          function to_varchar2(p_old clob, p_length number, p_rowid varchar2)
                                          return varchar2
                                          as
                                             v_length number:= dbms_lob.getlength(p_old);
                                          begin
                                          
                                             if v_length > p_length then
                                               raise_application_error(-20001,'Found a value longer than target column length: '||to_char(v_value)||' at rowid: '||p_rowid);
                                             end if;
                                             
                                             return dbms_lob.substr(p_old,v_length,1);
                                             
                                          end;
                                     begin
                                          for c_cur in (select rowid rwid, ${oid(column.'@name')} from ${oid(table.'@name')}) loop
                                             v_value:= to_varchar2(c_cur.${oid(column.'@name')},${target_length},c_cur.rwid);
                                             update ${oid(table.'@name')}
                                                set mvn_${tempIndex}= v_value
                                              where rowid= c_cur.rwid;
                                          end loop;
                                          commit;
                                     end;
                                   """
                     ]
          
          changes << [
                              type: 'maven_drop_column_after_data_migration',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} drop column ${oid(column.'@name')}",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]

          changes << [
                              type: 'maven_rename_temporary_column',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} rename column mvn_${tempIndex} to ${oid(column.'@name')}",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]
          
          return changes;
      }
      
      private column_number_to_varchar2(table,column)
      {
          def changes= [];
          def tempIndex= tempColIndex--;
          def columnType= getColumnType(column);
          
          try
          {
             changes << [
                             type: 'drop_maven_temporary_column',
                         mainType: 'modify_column',
                        tempIndex: tempIndex,
                              ddl: "alter table ${oid(table.'@name')} drop column mvn_${tempIndex}",
                         failSafe: true
                        ]
          }
          catch (Exception ex)
          {}
          
          changes << [
                              type: 'add_maven_temporary_column',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} add (mvn_${tempIndex} ${columnType})",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]
          
          def target_length= column.'@length';

          changes << [
                              type: 'maven_translate_values_number_to_varchar2',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: """declare
                                          v_value ${columnType};
                                          
                                          function to_varchar2(p_old number, p_length number, p_rowid varchar2)
                                          return varchar2
                                          as 
                                             v_value varchar2(2000):= to_char(p_old);
                                          begin
                                          
                                             if length(v_value) > p_length then
                                               raise_application_error(-20001,'Found a value longer than target column length: '||to_char(v_value)||' at rowid: '||p_rowid);
                                             end if;
                                             
                                             return v_value;
                                             
                                          end;
                                     begin
                                          for c_cur in (select rowid rwid, ${oid(column.'@name')} from ${oid(table.'@name')}) loop
                                             v_value:= to_varchar2(c_cur.${oid(column.'@name')},${target_length},c_cur.rwid);
                                             update ${oid(table.'@name')}
                                                set mvn_${tempIndex}= v_value
                                              where rowid= c_cur.rwid;
                                          end loop;
                                          commit;
                                     end;
                                   """
                     ]
          
          changes << [
                              type: 'maven_drop_column_after_data_migration',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} drop column ${oid(column.'@name')}",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]

          changes << [
                              type: 'maven_rename_temporary_column',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} rename column mvn_${tempIndex} to ${oid(column.'@name')}",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]
          
          return changes;
      }
      
      
      private column_number_reduce_precision(table,column)
      {
          def changes= [];
          def tempIndex= tempColIndex--;
          def columnType= getColumnType(column);
          
          try
          {
             changes << [
                             type: 'drop_maven_temporary_column',
                         mainType: 'modify_column',
                        tempIndex: tempIndex,
                              ddl: "alter table ${oid(table.'@name')} drop column mvn_${tempIndex}",
                         failSafe: true
                        ]
          }
          catch (Exception ex)
          {}
          
          changes << [
                              type: 'add_maven_temporary_column',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} add (mvn_${tempIndex} ${columnType})",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]
          
          changes << [
                              type: 'maven_column_reduce_number_precision',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: """declare
                                          v_value ${columnType};
                                          
                                          function reduce_precision(p_old number, p_rowid varchar2)
                                          return number
                                          as
                                             v_value ${columnType};
                                          begin
                                          
                                             v_value:= p_old;
                                             return v_value;
                                          
                                          exception
                                           when others then
                                             raise_application_error(-20001,'Found value incompatible with target column precision: '||to_char(p_old)||' at rowid: '||p_rowid);
                                          end;
                                     begin
                                          for c_cur in (select rowid rwid, ${oid(column.'@name')} from ${oid(table.'@name')}) loop
                                             v_value:= reduce_precision(c_cur.${oid(column.'@name')},c_cur.rwid);
                                             update ${oid(table.'@name')}
                                                set mvn_${tempIndex}= v_value
                                              where rowid= c_cur.rwid;
                                          end loop;
                                          commit;
                                     end;
                                   """
                     ]
          
          changes << [
                              type: 'maven_drop_column_after_data_migration',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} drop column ${oid(column.'@name')}",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]

          changes << [
                              type: 'maven_rename_temporary_column',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} rename column mvn_${tempIndex} to ${oid(column.'@name')}",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]
          
          return changes;
      }
	  
	  public reorder(changes)
	  {
		  def reordered= changes.clone()
		  
		  changes.each
		  {
			  change ->
			  
			  if (change.type=='add_constraint'&&change.constraint.type=='foreign')
			  {
				  reordered.remove(change)
				  reordered << change
		      }
	      }
		  
		  return reordered
      }
      
}
