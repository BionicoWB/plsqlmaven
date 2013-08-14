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
		  def dbconstraints= constraints.clone()
		  
          xml.table('name': xid(name))
          {
			  def comment= sql.firstRow("select comments from user_tab_comments where table_name = ${name}")?.comments
			  
			  if (comment)
			    xml.comment('')
				{
					out.print("<![CDATA[${comment}]]>")
			    }
			  
              xml.columns()
              {
                  sql.eachRow("select * from user_tab_columns a where table_name = ${name} order by column_id")
                  {
                     def col= it.toRowResult()
                     def p= col.data_type.indexOf('(')
				 	 def colComment= sql.firstRow("select comments from user_col_comments where table_name = ${name} and column_name= ${col.column_name}")?.comments
                     def forcedLengthSemantic= System.getProperty('forceLengthSemantic')

                     xml.column('name':          xid(col.column_name),
                                'type':          col.data_type.toLowerCase().substring(0,(p==-1?col.data_type.length():p)),
                                'precision':     col.data_precision,
                                'scale':         col.data_scale,
                                'length':        rd(col.char_length+(col.char_length ? (forcedLengthSemantic ? ' '+forcedLengthSemantic : (col.char_used==null ? 0 : ' '+(col.char_used=='B' ? 'byte' : 'char'))) : 0),0),
                                'default':       (col.data_default?.trim()?.toLowerCase()=='null' ? null : col.data_default?.trim()),
                                'primary':       simpleKey(col.column_name,constraints, 'P'),
                                'unique':        simpleKey(col.column_name,constraints, 'U'),
                                'check':         simpleCheck(col.column_name,constraints),
                                'not-null':      simpleNotNull(col.column_name,constraints,dbconstraints),
                                'references':    simpleForeignKey(col.column_name,constraints))
					 {
					    if (colComment)
						  xml.comment('')
						  {
							 out.print("<![CDATA[${colComment}]]>")
						  }
				     }
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
                                                 'owner': xid(constraint.rcolumns[0].owner)==xid(username) ? null : xid(constraint.rcolumns[0].owner))
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
                  
               def triggers= sql.rows("select trigger_name from user_triggers a where table_name = ${name}");
               
               if (triggers.size()>0)   
                   xml.triggers()
                   {
                       triggers.each
                       {
                          trigger ->
                          
                          xml.trigger('name': xid(trigger.trigger_name))
                       }
                   }
              
          }
          
          return true;
      }
      
      private getConstraints(tableName)
      {
          def constraints= []
          
          sql.eachRow("select * from user_constraints a where table_name = upper(${tableName}) order by decode(constraint_type,'P',1,'U',2,'R',3,4)")
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
                                   &&constraint.constraint_name.startsWith('SYS_C')
                                   &&constraint.columns.size()==1
                                   &&constraint.columns.find{ column -> (column.column_name==columnName) }) } 
          
          constraints.remove constraint
          
          return constraint ? 'true' : null
      }
      
      private simpleForeignKey(columnName,constraints)
      {
          def fk= constraints.find{ constraint -> (constraint.constraint_type=='R'
                                           &&constraint.constraint_name.startsWith('SYS_C')
                                           &&constraint.columns.size()==1
                                           &&constraint.columns.find{ column -> (column.column_name==columnName) }) }
          
          if (fk)
          {          
             constraints.remove fk
             def rcol= fk.rcolumns[0]             
             return ((xid(username)!=xid(rcol.owner) ? xid(rcol.owner)+'.' : '')+xid(rcol.table_name)+(rcol.column_name!= columnName ? '.'+xid(rcol.column_name) : ''))
          }
          
          return null
      }

      private simpleCheck(columnName,constraints)
      {
          def checks= constraints.findAll{ constraint -> (constraint.constraint_type=='C'
                                                     && constraint.columns.find{ column -> (column.column_name==columnName) }
                                                     && constraint.columns.size()==1
                                                     && constraint.constraint_name.startsWith('SYS_C') 
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
      
      private simpleNotNull(columnName,constraints,dbconstraints)
      {
          def colInPk= (boolean)dbconstraints.find{ constraint -> (constraint.constraint_type=='P'
                                                                 &&constraint.columns.find{ column -> (column.column_name==columnName) }) }
          
          def constraint= constraints.findAll{ constraint -> (cmp(constraint.constraint_type,'C')
                                                 && constraint.columns.find{ column -> cmp(column.column_name,columnName) }
                                                 && cmp(constraint.columns.size(),1)
                                                 && constraint.constraint_name.startsWith('SYS_C') 
                                                 && isNotNullSearchCondition(constraint.search_condition,columnName)) } 
          
          if (constraint.size()>0)
          {
            constraint.each { c -> constraints.remove c } // it is possible to find doubled check not null constraint on the same column
            return colInPk ? null : 'true'
          }
          else
            return null
           
      }
      
      public boolean exists(table)
      {
           def exists= false;
           sql.eachRow("select 1 from user_tables where table_name= ${oid(table.'@name',false)}")
           { exists= true }
           
           return exists
      }
      
      public create(table)
      {
          def changes= []
		  
		  def maxLength= 0;
		  
		  table.columns.column.each
		  { 
			  col -> 
			  
			  def l=oid(col.'@name').length() 
			  
			  if (l>maxLength)
			    maxLength= l
		  }
		  
		  def spaces= 
		  {
		      col ->
			  
			  def sp= ''
			  
			  (maxLength-oid(col.'@name').length()+2).times{ sp+=' ' }
			  
			  return sp
		  }
          
          changes << [
                              type: 'create_table',
                               ddl: 'create table '+oid(table.'@name')+'\n(\n'+INDENT+table.columns.column.collect{ col-> oid(col.'@name')+spaces(col)+getColumnType(col) }.join(',\n'+INDENT)+'\n)',
                       privMessage: "You need to: grant create table to ${username}"         
                     ]
          
		  if (table.comment)
   		    changes << set_table_comment(table)

          table.columns.column.each
          {
              column ->
			  
			  if (column.comment)
	    		    changes << set_column_comment(table,column)
              
              if (column.'@primary'=='true')
                changes << add_constraint(table,col_to_primary(column))

              if (column.'@not-null'=='true')
                changes << add_constraint(table,col_to_notnull(column))

              if (column.'@unique'=='true')
                changes << add_constraint(table,col_to_unique(column))
                
              if (column.'@check')
                changes << add_constraint(table,col_to_check(column,column.'@check'))
                
              if (column.'@references')
                changes << add_constraint(table,col_to_foreign(column))

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
          
		  if (source.comment?.text()!=target.comment?.text())
		    changes << set_table_comment(target)
			
		 log.debug 'source: '+source.toString()
			
          target.columns.column.each
          {
                targetCol ->
                
                def sourceCol= source.columns.column.find({ col -> cmp(col.'@name',targetCol.'@name') })
				
				log.debug 'sourceCol: '+sourceCol.toString()
                
                if (!sourceCol)
                {
                      changes << add_column(target,targetCol)
                    
					  if (targetCol.comment)
						changes << set_column_comment(target,targetCol)
	
	                  if (targetCol.'@primary'=='true')
	                    changes << add_constraint(target,col_to_primary(targetCol))
	    
	                  if (targetCol.'@not-null'=='true')
	                    changes << add_constraint(target,col_to_notnull(targetCol))
	    
	                  if (targetCol.'@unique'=='true')
	                    changes << add_constraint(target,col_to_unique(targetCol))
	                    
	                  if (targetCol.'@check')
	                    changes << add_constraint(target,col_to_check(targetCol,targetCol.'@check'))
	                    
	                  if (targetCol.'@references')
	                    changes << add_constraint(target,col_to_foreign(targetCol))
    
                }
                else
                {
					if (sourceCol.comment?.text()!=targetCol.comment?.text())
		   			   changes << set_column_comment(target,targetCol)
		
                    if (  !cmp(sourceCol,targetCol,'type')
                        ||!cmp(sourceCol,targetCol,'precision')
                        ||!cmp(sourceCol,targetCol,'scale')
                        ||!cmp(sourceCol,targetCol,'length')
                        ||!cmp(sourceCol,targetCol,'default'))
                      changes += modify_column(target,targetCol,sourceCol)
                    
                    if (!cmp(sourceCol,targetCol,'primary','false'))
                      modify_simple_primary(changes,target,targetCol,sourceCol)

                    if (!cmp(sourceCol,targetCol,'unique','false'))
                      modify_simple_unique(changes,target,targetCol,sourceCol)

                    if (!cmp(sourceCol,targetCol,'not-null','false'))
                      modify_simple_notnull(changes,target,targetCol,sourceCol)
					  
                    if (!cmp(sourceCol,targetCol,'check'))
                      modify_simple_check(changes,target,targetCol,sourceCol)
                      
                    if (!cmp(sourceCol,targetCol,'references'))
                      modify_simple_foreign(changes,target,targetCol,sourceCol)
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
                    
                    if (targetCons)
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
				 
                 def sourceCons= source.constraints.constraint.find{ c-> (c.'@name'==targetCons.'@name') }

				 if (!sourceCons)
                 {
                     if (!(targetCons.'@name' in renamedConstraints))
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
                            ||!cmp(sourceCons.references[0]?.'@table',targetCons.references[0]?.'@table') )
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
          
          source.triggers.trigger.each
          {
              sourceTrigger ->
              
              def targetTrigger= target.triggers.trigger.find{ t-> (t.'@name'==sourceTrigger.'@name')}
              
              if (!targetTrigger)
                changes << drop_trigger(source,sourceTrigger)
          }
          
          return changes
      }
   
      
      /*   CHANGES    */
      
      public drop_trigger(table,trigger)
      {
           return [
                            type: 'drop_trigger',
                             ddl: "drop trigger ${oid(trigger.'@name')}",
                     privMessage: "You need to: grant alter table to ${username}"
                  ]
      }
      
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
          if (targetCol.'@type'=='number'&&sourceCol.'@type' in ['varchar2','char'])
          {
              changes+= column_varchar2_to_number(table,targetCol);
          }
          else
          if (targetCol.'@type'=='number'&&sourceCol.'@type'=='number'&&
              ((!cmp(sourceCol,targetCol,'precision')
                  &&(!(dv(targetCol.'@precision'?.toInteger(),9999999)>dv(sourceCol.'@precision'?.toInteger(),9999999))))
              ||!cmp(sourceCol,targetCol,'scale',0)))
          {
              changes+= column_number_reduce_precision(table,targetCol);
          }
          else
          if (targetCol.'@type'=='varchar2'&&sourceCol.'@type'=='varchar2'&&
              !cmp(sourceCol,targetCol,'length'))
              changes+= column_varchar2_change_length(table,targetCol,sourceCol);
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
		  def ddl= "alter table ${oid(table.'@name')} "
		  
		  if (constraint.'@type'=='not-null')
          {
              if (System.getProperty("disableConstraints")!=null)
               ddl+= "add check (${constraint.columns.column[0].'@name'} is not null) disable"
              else
               ddl+= "modify (${constraint.columns.column[0].'@name'} not null)"
          }
          else
		  {
			  ddl+= "add "+(constraint.'@name' ? "constraint ${oid(constraint.'@name')}\n" : '')

              def tbs= System.getProperty("indexTablespace")
			  
	          if (constraint.'@type'=='primary')
	              ddl+="primary key ("+constraint.columns.column*.'@name'.join(',')+")"+(tbs!=null ? " using index tablespace ${tbs}" : "")
	          else
	          if (constraint.'@type'=='foreign')
	          {
	              ddl+="foreign key ("+constraint.columns.column*.'@name'.join(',')+")\n"
	              def owner= constraint.references[0].'@owner'
	              ddl+="references "+( owner&&owner!=username ? owner+'.' : '' )+constraint.references[0].'@table'
	              ddl+='('+constraint.references.column*.'@name'.join(',')+')'
	              
	              if (dv(constraint.'@on-delete','no action')!='no action')
	                ddl+='\non delete '+constraint.'@on-delete'
	          }
	          else
	          if (constraint.'@type'=='check')
	              ddl+="check ("+constraint.'@expression'+")"
	          else
	          if (constraint.'@type'=='unique')
	              ddl+="unique ("+constraint.columns.column*.'@name'.join(',')+")"+(tbs!=null ? " using index tablespace ${tbs}" : "")

              if (System.getProperty("disableConstraints")!=null)
	              ddl+=" disable";
                
		  }
          
          return [
                         type: 'add_constraint',
                          ddl: ddl,
                  privMessage: "You need to: grant alter table to ${username}",
                   constraint: [type: constraint.'@type']
                 ]

      }
   
      public rename_constraint(table,sourceCons,targetCons)
      {
		  def position=1;		  
		  def positionQuery= targetCons.columns.column.collect{ """select constraint_name from user_cons_columns where column_name= '${oid(it.'@name',false)}' and position= ${position++} and table_name= v_table""" }.join(' intersect ')
		  
          return [
                              type: 'rename_constraint',
                             //  ddl: "alter table ${oid(table.'@name')} rename constraint ${oid(sourceCons.'@name')} to ${oid(targetCons.'@name')}",
							   ddl: """-- rename ${targetCons.'@type'} constraint on ${oid(table.'@name')}(${targetCons.columns.column.collect{ oid(it.'@name') }.join(',')}) to ${targetCons.'@name'}  
                                       declare
                                         v_table       varchar2(30):= '${oid(table.'@name',false)}';
                                         v_constraint  varchar2(30):= '${oid(sourceCons.'@name',false)}';
                                       begin
                                         
                                         if v_constraint= 'null' then
	                                         select constraint_name
	                                           into v_constraint
	                                           from user_constraints
	                                          where constraint_name in (${positionQuery})
	                                            and constraint_type= 'P'
	                                            and constraint_name like 'SYS\\_C%' escape '\\';
                                         end if;
                                         
                                         execute immediate 'alter table "'||v_table||'" rename constraint "'||v_constraint||'" to ${oid(targetCons.'@name')}';
                                       
                                       end;""",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
      }
	  
	  public drop_primary_constraint(table,constraint)
	  {
		  return [
							  type: 'drop_constraint',
							   ddl: "alter table ${oid(table.'@name')} drop primary key cascade drop index",
					   privMessage: "You need to: grant alter table to ${username}"
				 ]
	  }

	  public drop_unique_constraint(table,constraint)
	  {

		  def position=1;		  
		  def positionQuery= constraint.columns.column.collect{ """select constraint_name from user_cons_columns where column_name= '${oid(it.'@name',false)}' and position= ${position++} and table_name= v_table""" }.join(' intersect ')
		  
          return [
                              type: 'drop_constraint',
                               ddl: """-- drop unique constraint on ${oid(table.'@name')}(${constraint.columns.column.collect{ oid(it.'@name') }.join(',')})
                                       declare
                                         v_table       varchar2(30):= '${oid(table.'@name',false)}';
                                         v_constraint  varchar2(30);
                                       begin
                                         
                                         select constraint_name
                                           into v_constraint
                                           from user_constraints
                                          where constraint_name in (${positionQuery})
                                            and constraint_type= 'U'
                                            and constraint_name like 'SYS\\_C%' escape '\\';
                                         
                                         execute immediate 'alter table "'||v_table||'" drop constraint "'||v_constraint||'" cascade drop index';
                                       
                                       end;""",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
	  }

	  public drop_check_constraint(table,constraint)
	  {

		  def position=1;		  
		  def columnNames= constraint.columns.column.collect{ "'"+oid(it.'@name',false)+"'" }.join(',')
		  
          return [
                              type: 'drop_constraint',
                               ddl: """-- drop check constraint (${constraint.'@expression'}) on ${oid(table.'@name')}
                                       declare
                                         v_table             varchar2(30):= '${oid(table.'@name',false)}';
                                         v_constraint        varchar2(30);
                                         v_search_condition  varchar2(32767):= q'{${constraint.'@expression'}}';
                                       begin
                                         
                                             for c_cur in (select constraint_name,
                                                                  search_condition
                                                             from user_constraints
                                                            where constraint_name in (select distinct constraint_name
                                                                                        from user_cons_columns a
                                                                                       where column_name in (${columnNames})
                                                                                         and position is null
                                                                                         and table_name= v_table) 
                                                              and constraint_name like 'SYS\\_C%' escape '\\'
                                                              and constraint_type= 'C')
                                             loop
                                              if c_cur.search_condition= v_search_condition then
                                                execute immediate 'alter table "'||v_table||'" drop constraint "'||c_cur.constraint_name||'"';
                                              end if;
                                             end loop;
                                         
                                       end;""",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
	  }
	  
	  public drop_foreign_constraint(table,constraint)
	  {

		  def position=1;		  
		  def positionQuery= constraint.columns.column.collect{ """select constraint_name from user_cons_columns where column_name= '${oid(it.'@name',false)}' and position= ${position++} and table_name= v_table""" }.join(' intersect ')
		  position=1;
		  def rPositionQuery= constraint.references.column.collect{ """select constraint_name from all_cons_columns where column_name= '${oid(it.'@name',false)}' and position= ${position++} and table_name= v_r_table and owner= v_r_owner""" }.join(' intersect ')
		  
          return [
                              type: 'drop_constraint',
                               ddl: """-- drop foreign constraint on ${oid(table.'@name')}(${constraint.columns.column.collect{ oid(it.'@name') }.join(',')}) to ${oid(constraint.references.'@table'[0])}(${constraint.references.column.collect{ oid(it.'@name') }.join(',')}) 
                                       declare
                                         v_table       varchar2(30):= '${oid(table.'@name',false)}';
                                         v_r_table     varchar2(30):= '${oid(constraint.references.'@table'[0],false)}';
                                         v_r_owner     varchar2(30):= nvl('${oid(constraint.references.'@owner'[0],false)}','null');
                                         v_constraint  varchar2(30);
                                       begin
                                       
                                         if v_r_owner = 'null' then
                                           v_r_owner:= user;
                                         end if;
                                         
                                         select constraint_name
                                           into v_constraint
                                           from user_constraints
                                          where constraint_name in (${positionQuery})
                                            and constraint_type= 'R'
                                            and constraint_name like 'SYS\\_C%' escape '\\'
                                            and r_constraint_name = (select constraint_name
                                                                       from all_constraints
                                                                      where constraint_name in (select constraint_name
										                                                         from all_cons_columns a
										                                                        where constraint_name in (${rPositionQuery})
										                                                          and owner = v_r_owner)
										                               and constraint_type in ('P','U')
										                               and owner = v_r_owner);
                           
                                         
                                         execute immediate 'alter table "'||v_table||'" drop constraint "'||v_constraint||'"';
                                       
                                       end;""",
                       privMessage: "You need to: grant alter table to ${username}"
                 ]
	  }

	  public drop_constraint(table,constraint)
      {
		  if (constraint.'@name')
	          return [
	                              type: 'drop_constraint',
	                               ddl: "alter table ${oid(table.'@name')} drop constraint ${oid(constraint.'@name')} cascade"+(constraint.'@type' in ['primary','unique'] ? ' drop index' : ''),
	                       privMessage: "You need to: grant alter table to ${username}"
	                 ]
		  else
          if (constraint.'@type'=='not-null')
	          return [
	                              type: 'drop_constraint',
	                               ddl: "alter table ${oid(table.'@name')} modify (${constraint.columns.column[0].'@name'} null)",
	                       privMessage: "You need to: grant alter table to ${username}"
	                 ]
          else
			  return "drop_${constraint.'@type'}_constraint"(table,constraint)
      }
      
      public modify_constraint(table,constraint,cause=null)
      {
          return [drop_constraint(table,constraint),
                  add_constraint(table,constraint)]
      }
	  
	  public col_to_primary(column)
	  {
		  return parser.parseText('<constraint type="primary"><columns><column name="'+column.'@name'+'"/></columns></constraint>')
      }
	  
	  public col_to_unique(column)
	  {
		  return parser.parseText('<constraint type="unique"><columns><column name="'+column.'@name'+'"/></columns></constraint>')
	  }

	  public col_to_check(column,expression)
	  {
		  return parser.parseText('<constraint type="check" expression="'+expression+'"><columns><column name="'+column.'@name'+'"/></columns></constraint>')
	  }

	  public col_to_notnull(column)
	  {
		  return parser.parseText('<constraint type="not-null"><columns><column name="'+column.'@name'+'"/></columns></constraint>')
	  }
	  
	  public col_to_foreign(column)
	  {
		  def ref= column.'@references'.split('\\.')
		  def rowner= ''
		  def rtable= ''
		  def rcolumn= ''
		  
		  if (ref.length==3)
		  {
		      rowner= ref[0]
		      rtable= ref[1]
		      rcolumn= ref[2]
		  }
		  else
		  if (ref.length==2)
		  {
		      rtable= ref[0]
		      rcolumn= ref[1]
	      }
		  else
		  {
		      rtable= ref[0]
		      rcolumn= column.'@name'
		  }
		  
		  return parser.parseText('<constraint type="foreign"><columns><column name="'+column.'@name'+'"/></columns><references table="'+rtable+'" owner="'+rowner+'"><column name="'+rcolumn+'"/></references></constraint>')
	  }
	  
	  public modify_simple_primary(changes,table,targetCol,sourceCol)
      {
          if (sourceCol.'@primary'=='true')
            changes << drop_constraint(table,col_to_primary(sourceCol))
          
          if (targetCol.'@primary'=='true')
            changes << add_constraint(table,col_to_primary(targetCol))
      }

      public modify_simple_unique(changes,table,targetCol,sourceCol)
      {
          if (sourceCol.'@unique'=='true')
            changes << drop_constraint(table,col_to_unique(sourceCol))
            
           if (targetCol.'@unique'=='true')
            changes << add_constraint(table,col_to_unique(targetCol))
      }

      public modify_simple_check(changes,table,targetCol,sourceCol)
      {
          if (sourceCol.'@check')
            changes << drop_constraint(table,col_to_check(sourceCol,sourceCol.'@check'))
            
          if (targetCol.'@check')
            changes << add_constraint(table,col_to_check(targetCol,targetCol.'@check'))
      }

      public modify_simple_notnull(changes,table,targetCol,sourceCol)
      {
          if (sourceCol.'@not-null'=='true')
            changes << drop_constraint(table,col_to_notnull(sourceCol))
            
          if (targetCol.'@not-null'=='true')
            changes << add_constraint(table,col_to_notnull(targetCol))
      }

      public modify_simple_foreign(changes,table,targetCol,sourceCol)
      {
          if (sourceCol.'@references')
            changes << drop_constraint(table,col_to_foreign(sourceCol))
            
          if (targetCol.'@references')
            changes << add_constraint(table,col_to_foreign(targetCol))
      }
      
      public column_varchar2_to_clob(table,column)
      {
          def changes= [];
          def tempIndex= tempColIndex--;
          def columnType= getColumnType(column);
          
          changes << [
	                         type: 'drop_maven_temporary_column',
	                     mainType: 'modify_column',
	                    tempIndex: tempIndex,
	                          ddl: "alter table ${oid(table.'@name')} drop column mvn_${tempIndex}",
	                     failSafe: true
                     ]
          
          changes << [
                              type: 'add_maven_temporary_column',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} add (mvn_${tempIndex} ${columnType})",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]
          
          def target_length= column.'@length'.split(' ')[0];

          changes << [
                              type: 'maven_translate_values_varchar2_to_clob',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: """begin update ${oid(table.'@name')} set mvn_${tempIndex}= ${oid(column.'@name')}; commit; end;"""
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
      
      public column_clob_to_varchar2(table,column)
      {
          def changes= [];
          def tempIndex= tempColIndex--;
          def columnType= getColumnType(column);
          
          changes << [
	                         type: 'drop_maven_temporary_column',
	                     mainType: 'modify_column',
	                    tempIndex: tempIndex,
	                          ddl: "alter table ${oid(table.'@name')} drop column mvn_${tempIndex}",
	                     failSafe: true
                     ]
          
          changes << [
                              type: 'add_maven_temporary_column',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: "alter table ${oid(table.'@name')} add (mvn_${tempIndex} ${columnType})",
                       privMessage: "You need to: grant alter table to ${username}"
                     ]
          
          def target_length= column.'@length'.split(' ')[0];

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
      
      private column_varchar2_to_number(table,column)
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
                              type: 'maven_translate_values_varchar2_to_number',
                          mainType: 'modify_column',
                         tempIndex: tempIndex,
                               ddl: """declare
                                          v_value ${columnType};
                                          
                                          function to_number2(p_old varchar2, p_rowid varchar2)
                                          return varchar2
                                          as 
                                             v_value ${columnType};
                                          begin
                                             v_value:= to_number(p_old);
                                             return v_value;
                                          exception
                                            when others then 
                                               raise_application_error(-20001,'Found an invalid number: '||p_old||' at rowid: '||p_rowid);
                                          end;
                                     begin
                                          for c_cur in (select rowid rwid, ${oid(column.'@name')} from ${oid(table.'@name')}) loop
                                             v_value:= to_number2(c_cur.${oid(column.'@name')},c_cur.rwid);
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
          
          def target_length= column.'@length'.split(' ')[0];

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
      
      
      private column_varchar2_change_length(table,column,source)
      {
          def changes= [];
          def tempIndex= tempColIndex--;
          def sourceLength= source.'@length'.split(' ');
          def targetLength= column.'@length'.split(' ');
          def columnType= getColumnType(column);
          

          if (sourceLength[0].toInteger()<=targetLength[0].toInteger())
              changes << [
                                  type: 'maven_column_alter_varchar2_length',
                              mainType: 'modify_column',
                             tempIndex: tempIndex,
                                   ddl: """alter table ${oid(table.'@name')} modify (${oid(column.'@name')} ${columnType})"""
                         ]
           
          else
          { 
          
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
                                  type: 'maven_column_change_varchar2_length',
                              mainType: 'modify_column',
                             tempIndex: tempIndex,
                                   ddl: """declare
                                              v_value ${columnType};
                                              
                                              function change_length(p_old varchar2, p_rowid varchar2)
                                              return varchar2
                                              as
                                                 v_value ${columnType};
                                              begin
                                              
                                                 v_value:= p_old;
                                                 return v_value;
                                              
                                              exception
                                               when others then
                                                 raise_application_error(-20001,'Found value incompatible with target column length: '||p_old||' at rowid: '||p_rowid);
                                              end;
                                         begin
                                              for c_cur in (select rowid rwid, ${oid(column.'@name')} from ${oid(table.'@name')}) loop
                                                 v_value:= change_length(c_cur.${oid(column.'@name')},c_cur.rwid);
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
             
          }
 
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
	  
	  public set_table_comment(table)
	  {
		  def escapedComment= table.comment?.text()?.replaceAll("'","''")
		  
		  return [
					  type: 'table_comment',
					   ddl: "comment on table ${oid(table.'@name')} is '${escapedComment}'",
			   privMessage: "You need to: grant alter table to ${username}"
                 ]
      }
      
	  public set_column_comment(table,column)
	  {
		  def escapedComment= column.comment?.text()?.replaceAll("'","''")
		  
		  return [
					  type: 'column_comment',
					   ddl: "comment on column ${oid(table.'@name')}.${oid(column.'@name')} is '${escapedComment}'",
			   privMessage: "You need to: grant alter table to ${username}"
                 ]
      }
	  
      public reorder(changes)
      {
          def reordered= changes.clone()
          
          changes.each
          {
              change ->
              
              if (change&&change.type=='add_constraint'&&change.constraint.type=='foreign')
              {
                  reordered.remove(change)
                  reordered << change
              }
          }
          
          return reordered
      }
      
}
