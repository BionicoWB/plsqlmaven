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

class IndexHelper extends OraDdlHelper
{
      public IndexHelper(sql,log,username)
      {
          super(sql,log,username);
      }

      public boolean extract(name,xml)
      {
           def extracted= false;
           sql.eachRow("""select * 
                            from user_indexes a
                           where index_name = upper(${name})
                             and generated= 'N'
                             and index_type!='CLUSTER'
                             and index_name not in (select index_name 
                                                      from user_constraints 
                                                     where index_name is not null)""")
           {
               def ind= it.toRowResult()
               xml.index('name':         name,
                         'table':        ind.table_name.toLowerCase(),
                         'unique':       (ind.uniqueness=='UNIQUE' ? 'true' : null))
               { 
                   xml.columns()
                   {
                       sql.eachRow("""select a.column_name,
                                             b.column_expression,
                                             a.descend
                                        from user_ind_columns a,
                                             user_ind_expressions b
                                       where b.column_position(+)= a.column_position
                                         and b.index_name(+)= a.index_name
                                         and a.index_name= upper(${name})
                                    order by a.column_position""")
                       {
                          def col= it.toRowResult()
                          
                          if (col.column_expression)
                              xml.column('expression':   col.column_expression.toLowerCase().replace('"',''), 
                                         'direction':    rd(col.descend,'ASC')?.toLowerCase());
                          else
                              xml.column('name':         col.column_name.toLowerCase(),
                                         'direction':    rd(col.descend,'ASC')?.toLowerCase());
    
                       }
                   }
               }
               extracted= true;
           }
           
           return extracted;
      }
      
      public boolean exists(index)
      {
           def exists= false;
           sql.eachRow("select 1 from user_indexes where index_name= upper(${index.'@name'})")
           { exists= true }
           
           return exists;
      }
      
      public create(index)
      {
          doddl("create"+(index.'@unique'=='true' ? ' unique' : '')+" index ${index.'@name'} on "+index.'@table'+"("+index.columns.column.collect{ if (it.'@name') return it.'@name' else return it.'@expression' }.join(',')+")",
                "You need to: grant create index to ${username}")
      }
      
      public List detectChanges(index)
      {
          def changes= [];
          def cnt= 0;
          
          def indexName= index.'@name'
          def existingCols= []
          
          sql.eachRow("""select index_name,
                                table_name, 
                                uniqueness
                           from user_indexes
                          where index_name= upper(${indexName})""")
          {
              def dbidx= it.toRowResult()
              
              if (index.'@table'!=dbidx.table_name.toLowerCase())
              {
                  changes << [type: 'index_change', index: index]
                  return;
              }

              if (dv(index.'@unique','false')!=(dbidx.uniqueness=='UNIQUE' ? 'true' : 'false'))
              {
                  changes << [type: 'index_change', index: index]
                  return;
              }
          }
          
          sql.eachRow("""select a.column_name,
                                b.column_expression,
                                a.descend
                           from user_ind_columns a,
                                user_ind_expressions b
                          where b.column_position(+)= a.column_position
                            and b.index_name(+)= a.index_name
                            and a.index_name= upper(${indexName})
                       order by a.column_position""")
          {
              def dbcol= it.toRowResult()
              def col= index.columns.column[cnt++]
              if (!col
                  ||dv(col.'@expression',col.'@name')!= dv(dbcol.column_expression?.replace('"',''),dbcol.column_name).toLowerCase()
                  ||dv(col.'@direction','asc')!=dv(dbcol.descend,'asc')?.toLowerCase())
              {
                  changes << [type: 'index_change', index: index]
                  return;
              }
          }
          
          // column added
          if (changes.size()==0&&index.columns.column[cnt++])
            changes << [type: 'index_change', index: index]
          
          return changes
      }
      
      /*   CHANGES    */
      
   public index_change(change)
   {
         doddl("drop index ${change.index.'@name'}",
               "You need to: grant drop index to ${username}")
         create(change.index)
   }
   
}
