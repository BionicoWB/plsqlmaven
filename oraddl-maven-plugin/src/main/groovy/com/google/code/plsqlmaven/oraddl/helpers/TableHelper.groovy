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

class TableHelper extends OraDdlHelper
{
      public TableHelper(sql,log,username)
      {
          super(sql,log,username);
      }

      public boolean extract(name,xml)
      {
          def constraints= getConstraints(name)
          
          xml.table('name': name)
          {
              xml.columns()
              {
                  sql.eachRow("select * from user_tab_columns a where table_name = upper(${name})")
                  {
                     def col= it.toRowResult()
                     def p= col.data_type.indexOf('(')
                     
                     xml.column('name':          col.column_name.toLowerCase(),
                                'type':          col.data_type.toLowerCase().substring(0,(p==-1?col.data_type.length():p)),
                                'precision':     col.data_precision,
                                'scale':         col.data_scale,
                                'length':        rd(col.char_length,0),
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
                          
                          xml.constraint('name': constraint.constraint_name.toLowerCase(),
                                         'type': constraintType(constraint.constraint_type),
                                         'expression': constraint.search_condition,
                                         'on-delete': constraint.delete_rule!='NO ACTION' ? constraint.delete_rule?.toLowerCase() : null)
                          {
                              if (constraint.constraint_type!='C')
                              xml.columns()
                              {
                                  constraint.columns.each
                                  {
                                     column ->
                                     
                                     xml.column('name': column.column_name.toLowerCase())
                                  }
                              }
                              
                              if (constraint.constraint_type=='R')
                              {
                                  
                                  xml.references('table': constraint.rcolumns[0].table_name.toLowerCase(),
                                                 'owner': constraint.rcolumns[0].owner==username.toUpperCase() ? null : constraint.rcolumns[0].owner.toLowerCase())
                                  {
                                      constraint.rcolumns.each
                                      {
                                         column ->
                                         
                                         xml.column('name': column.column_name.toLowerCase())
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
          
          sql.eachRow("select * from user_constraints a where table_name = upper(${tableName})")
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
             return ((username.toUpperCase()!=rcol.owner ? rcol.owner+'.' : '')+rcol.table_name+(rcol.column_name!= columnName ? '.'+rcol.column_name : '')).toLowerCase()
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
           sql.eachRow("select 1 from user_tables where table_name= upper(${table.'@name'})")
           { exists= true }
           
           return exists
      }
      
      public create(table)
      {
          doddl('create table '+(table.'@name')+'('+table.columns.column.collect{ col-> col.'@name'+' '+getColumnType(col) }.join(',')+')',
                "You need to: grant create table to ${username}")
         
          detectChanges(table).each{ "${it.type}"(it) }
      }
      
      public List detectChanges(table)
      {
          def changes= [];
          def cnt= 1;
          
          def tableName= table.'@name'
          def dbconstraints= getConstraints(tableName)
          
          def existingCols= []
          
          sql.eachRow("select * from user_tab_columns where table_name = upper(${tableName}) order by column_id")
          {
              def dbcol= it.toRowResult()
              def columnName= dbcol.column_name.toLowerCase()
              def col= table.columns.column.find({ col -> col.'@name'== columnName })
              def p= dbcol.data_type.indexOf('(')
              def dataType= dbcol.data_type.toLowerCase().substring(0,(p==-1?dbcol.data_type.length():p))
              existingCols << columnName
                         
              if (!col)
                changes << [type: 'drop_column', column: columnName, table: tableName]
              else
              {
                  if (dataType!=col.'@type'
                      ||!cmp(dbcol.data_precision,col.'@precision')
                      ||!cmp(dbcol.data_scale,col.'@scale')
                      ||!cmp(dbcol.char_length,dv(col.'@length',0)))
                    changes << [type: 'modify_column', column: columnName, columnType: getColumnType(col), table: tableName]
                    
                  if (col.'@primary'=='true'&&simpleKey(dbcol.column_name,dbconstraints, 'P')==null)
                      changes << [type:       'add_simple_primary',
                                  table:      tableName,
                                  column:     dbcol.column_name]
                  else
                  if (col.'@primary'==null&&simpleKey(dbcol.column_name,dbconstraints, 'P')=='true')
                      changes << [type:       'drop_simple_primary',
                                  table:      tableName,
                                  column:     dbcol.column_name]

                  if (col.'@unique'=='true'&&simpleKey(dbcol.column_name,dbconstraints, 'U')==null)
                      changes << [type:       'add_simple_unique',
                                  table:      tableName,
                                  column:     dbcol.column_name]
                  else
                  if (col.'@unique'==null&&simpleKey(dbcol.column_name,dbconstraints, 'U')=='true')
                      changes << [type:       'drop_simple_unique',
                                  table:      tableName,
                                  column:     dbcol.column_name]

                  if (col.'@check'!=null&&simpleCheck(dbcol.column_name,dbconstraints)==null)
                      changes << [type:       'add_simple_check',
                                  table:      tableName,
                                  check:      col.'@check']
                  else
                  if (col.'@check'==null&&simpleCheck(dbcol.column_name,dbconstraints)!=null)
                      changes << [type:                  'drop_simple_check',
                                  table:                 tableName,
                                  column:                dbcol.column_name]
                  else
                  if (col.'@check'!=simpleCheck(dbcol.column_name,dbconstraints))
                      changes << [type:                 'change_simple_check',
                                  table:                 tableName,
                                  column:                dbcol.column_name,
                                  check:                 col.'@check']
    
                  if (col.'@not-null'=='true'&&simpleNotNull(dbcol.column_name,dbconstraints)==null)
                      changes << [type:       'add_simple_notnull',
                                  table:      tableName,
                                  column:     dbcol.column_name]
                  else
                  if (col.'@not-null'==null&&simpleNotNull(dbcol.column_name,dbconstraints)=='true')
                      changes << [type:       'drop_simple_notnull',
                                  table:      tableName,
                                  column:     dbcol.column_name]
                      
                  if (col.'@references'!=null&&simpleForeignKey(dbcol.column_name,dbconstraints)==null)
                      changes << [type:       'add_simple_foreign',
                                  table:      tableName,
                                  column:     col.'@name',
                                  references: col.'@references']
                  else
                  if (col.'@references'==null&&simpleForeignKey(dbcol.column_name,dbconstraints)!=null)
                      changes << [type:       'drop_simple_foreign',
                                  table:      tableName,
                                  column:     col.'@name',
                                  references: col.'@references']
                  else
                  if (col.'@references'!=simpleForeignKey(dbcol.column_name,dbconstraints))
                      changes << [type:       'change_simple_foreign',
                                  table:      tableName,
                                  column:     col.'@name',
                                  references: col.'@references']
    
              }
          }
          
          changes+= table.columns.column.findAll{ c -> (!(c.'@name' in existingCols))}
                                        .collect{ c -> [type:       'add_column',
                                                        column:     c.'@name',
                                                        columnType: getColumnType(c),
                                                        table:      tableName] }
          
          
          dbconstraints.findAll{ c -> (!(c.constraint_name.toLowerCase() in table.constraints.constraint*.'@name')) }.each
          {
                constraint ->
                 
                changes << [type:       'drop_constraint',
                            table:      tableName,
                            constraint: constraint.constraint_name.toLowerCase()]
          }
              
          table.constraints.constraint.each
          {
                 constraint ->

                 try
                 {
                     def exit= new ContextException()
                     exit.context= [type:       'constraint_change',
                                    table:      tableName,
                                    constraint: constraint];
                                  
                     def dbconstraint= dbconstraints.find{ c-> (c.constraint_name.toLowerCase()==constraint.'@name')} 
                     
                     if (!dbconstraint)
                        changes << [type:       'add_constraint',
                                    table:      tableName,
                                    constraint: constraint]
                     else
                     {
                        if (!(cmp(constraintType(dbconstraint.constraint_type),constraint.'@type')
                            ||cmp(rd(dbconstraint.delete_rule?.toLowerCase(),'no action'),constraint.'@on-delete')
                            ||cmp(dbconstraint.search_condition,constraint.'@expression')
                            ||cmp(dbconstraint.rcolumns[0]?.owner,constraint.references[0]?.'@owner')
                            ||cmp(dbconstraint.rcolumns[0]?.table_name,constraint.references[0]?.'@table')))
                        {
                            exit.context['cause']= 'base constraint metadata differs'
                            throw exit
                        }
                        else
                        if (constraint.'@type'!='check')
                        {
                            cnt= 0;
                            
                            dbconstraint.columns.each
                            {
                                dbcolumn -> 
                                
                                if (dbcolumn.column_name.toLowerCase()!=constraint.columns.column[cnt]?.'@name')
                                {
                                    exit.context['cause']= 'columns order or names or count differs'
                                    throw exit
                                }
                                
                                cnt++;
                            }
                            
                            if (constraint.columns.column[cnt])
                            {
                                exit.context['cause']= 'column count differs'
                                throw exit
                            }
                               
                            if (constraint.'@type'=='foreign')
                            {
                                cnt= 0;
                                
                                dbconstraint.rcolumns.each
                                {
                                    dbrcolumn ->
                                    
                                    if (dbrcolumn.column_name.toLowerCase()!=constraint.references.column[cnt]?.'@name')
                                    {
                                        exit.context['cause']= 'referenced columns order or names or count differs'
                                        throw exit
                                    }
                                    
                                    cnt++;
                                }
                                
                                if (constraint.references.column[cnt])
                                {
                                    exit.context['cause']= 'referenced column count differs'
                                    throw exit
                                }
                            }
                        }
                     }
                     
                 }
                 catch (ContextException ex)
                 {
                       changes << ex.context
                 }
          }
                     
          return changes
      }
   
      
      /*   CHANGES    */
      
      public drop_column(change)
      {
          doddl("alter table ${change.table} drop column ${change.column}",
                "You need to: grant alter table to ${username}")
      }
   
      public modify_column(change)
      {
          doddl("alter table ${change.table} modify (${change.column} ${change.columnType})",
                "You need to: grant alter table to ${username}")
      }
   
      public add_column(change)
      {
          doddl("alter table ${change.table} add (${change.column} ${change.columnType})",
                "You need to: grant alter table to ${username}")
      }
      
      public add_constraint(change)
      {
          def ddl= "alter table ${change.table} add constraint ${change.constraint.'@name'} ";
          
          if (change.constraint.'@type'=='primary')
              ddl+="primary key ("+change.constraint.columns.column*.'@name'.join(',')+")"
          else
          if (change.constraint.'@type'=='foreign')
          {
              ddl+="foreign key ("+change.constraint.columns.column*.'@name'.join(',')+")"
              def owner= change.constraint.references[0].'@owner'
              ddl+=" references "+( owner&&owner!=username ? owner+'.' : '' )+change.constraint.references[0].'@table'
              ddl+='('+change.constraint.references.column*.'@name'.join(',')+')'
          }
          else
          if (change.constraint.'@type'=='check')
              ddl+="check ("+change.constraint.'@expression'+")"
          else
          if (change.constraint.'@type'=='unique')
              ddl+="unique ("+change.constraint.columns.column*.'@name'.join(',')+")"

          
          doddl(ddl,"You need to: grant alter table to ${username}")

      }
   
      public drop_constraint(change)
      {
          doddl("alter table ${change.table} drop constraint ${change.constraint}",
                "You need to: grant alter table to ${username}")
      }
      
      public constraint_change(change)
      {
          doddl("alter table ${change.table} drop constraint ${change.constraint.'@name'}",
                "You need to: grant alter table to ${username}")
          add_constraint(change);
      }

      public add_simple_primary(change)
      {
          doddl("alter table ${change.table} add primary key (${change.column})",
                "You need to: grant alter table to ${username}")
      }

      public drop_simple_primary(change)
      {
          doddl("alter table ${change.table} drop primary key",
                "You need to: grant alter table to ${username}")
      }

      public add_simple_unique(change)
      {
          doddl("alter table ${change.table} add unique (${change.column})",
                "You need to: grant alter table to ${username}")
      }

      public drop_simple_unique(change)
      {
          doddl("alter table ${change.table} drop constraint "+sql.firstRow("""select a.constraint_name
                                                                                 from user_constraints a,
                                                                                      user_cons_columns b
                                                                                where a.constraint_name= b.constraint_name
                                                                                  and a.table_name= b.table_name
                                                                                  and a.table_name= upper(${change.table})
                                                                                  and b.column_name= upper(${change.column})
                                                                                  and a.generated= 'GENERATED NAME'
                                                                                  and a.constraint_type= 'U'""").constraint_name,
                "You need to: grant alter table to ${username}")
      }

      public add_simple_check(change)
      {
          doddl("alter table ${change.table} add check (${change.check})",
                "You need to: grant alter table to ${username}")
      }

      public drop_simple_check(change)
      {
          doddl("alter table ${change.table} drop constraint "+sql.firstRow("""select a.constraint_name
                                                                                 from user_constraints a,
                                                                                      user_cons_columns b
                                                                                where a.constraint_name= b.constraint_name 
                                                                                  and b.column_name= upper(${change.column})
                                                                                  and a.table_name= b.table_name
                                                                                  and a.table_name= upper(${change.table})
                                                                                  and a.generated= 'GENERATED NAME'
                                                                                  and a.constraint_type= 'C'
                                                                                  and (select count(*) 
                                                                                         from user_cons_columns
                                                                                        where constraint_name= a.constraint_name)= 1""").constraint_name,
                "You need to: grant alter table to ${username}")
      }

      public change_simple_check(change)
      {
          drop_simple_check(change)
          add_simple_check(change)
      }

      public add_simple_notnull(change)
      {
          doddl("alter table ${change.table} add check (${change.column} is not null)",
                "You need to: grant alter table to ${username}")
      }

      public drop_simple_notnull(change)
      {
          doddl("alter table ${change.table} drop constraint "+sql.rows("""select a.constraint_name,
                                                                                  a.search_condition
                                                                             from user_constraints a,
                                                                                  user_cons_columns b
                                                                            where a.constraint_name= b.constraint_name 
                                                                              and b.column_name= upper(${change.column})
                                                                              and a.table_name= b.table_name
                                                                              and a.table_name= upper(${change.table})
                                                                              and a.generated= 'GENERATED NAME'
                                                                              and a.constraint_type= 'C'
                                                                              and (select count(*) 
                                                                                     from user_cons_columns
                                                                                    where constraint_name= a.constraint_name)= 1""").find{ row -> isNotNullSearchCondition(row.search_condition,change.column)}.constraint_name,
                "You need to: grant alter table to ${username}")
      }

      public add_simple_foreign(change)
      {
          def ref= change.references.split('\\.')
          def references;
          
          if (ref.length==3)
            references= ref[0]+'.'+ref[1]+'('+ref[2]+')'
          else
          if (ref.length==2)
            references= ref[0]+'('+ref[1]+')'
          else
            references= ref[0]+'('+change.column+')'
  
          doddl("alter table ${change.table} add foreign key (${change.column}) references ${references}",
                "You need to: grant alter table to ${username}")
      }

      public drop_simple_foreign(change)
      {
          doddl("alter table ${change.table} drop constraint "+sql.firstRow("""select a.constraint_name
                                                                                 from user_constraints a,
                                                                                      user_cons_columns b
                                                                                where a.constraint_name= b.constraint_name
                                                                                  and a.table_name= b.table_name
                                                                                  and a.table_name= upper(${change.table})
                                                                                  and b.column_name= upper(${change.column})
                                                                                  and a.generated= 'GENERATED NAME'
                                                                                  and a.constraint_type= 'R'""").constraint_name,
                "You need to: grant alter table to ${username}")
      }

      public change_simple_foreign(change)
      {
          drop_simple_foreign(change)
          add_simple_foreign(change)
      }

}
