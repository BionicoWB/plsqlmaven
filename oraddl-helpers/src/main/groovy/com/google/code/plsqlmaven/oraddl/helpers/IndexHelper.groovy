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
               xml.index('name':         xid(ind.index_name),
                         'table':        xid(ind.table_name),
                         'unique':       (ind.uniqueness=='UNIQUE' ? 'true' : null),
                         'bitmap':       (ind.index_type=='BITMAP' ? 'true' : null))
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
                                         and a.index_name= upper(${ind.index_name})
                                    order by a.column_position""")
                       {
                          def col= it.toRowResult()
                          
                          if (col.column_expression)
                              xml.column('expression':   col.column_expression, 
                                         'direction':    xid(rd(col.descend,'ASC')));
                          else
                              xml.column('name':         xid(col.column_name),
                                         'direction':    xid(rd(col.descend,'ASC')));
    
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
           sql.eachRow("select 1 from user_indexes where index_name= upper(${oid(index.'@name',false)})")
           { exists= true }
           
           return exists;
      }
      
      public create(index)
      {
          def ddl = "create"+(index.'@unique'=='true' ? ' unique' : '')+
                     (index.'@bitmap'=='true' ? ' bitmap' : '')+
                    " index ${oid(index.'@name')} on "+
                        "${oid(index.'@table')} ("+
                        index.columns.column.
                         collect{ indexPart(it) }.
                         join(',')+
                        ")"
 
          if (index.'@bitmap'=='true')
            ddl= """declare
                      v_ddl   varchar2(32767):= '${ddl}';
                      v_dummy varchar2(4000);
                    begin
                      select banner
                        into v_dummy 
                        from v$version
                       where banner like '%Enterprise%'
                         and rownum < 2;
                      execute immediate v_ddl;
                    exception
                       when no_data_found then
                      execute immediate replace(v_ddl,'create bitmap ','create ');
                    end;"""
                        
          return [ 
                          type: 'create_index', 
                           ddl: ddl,
                   privMessage: "You need to: grant create index to ${username}" 
                 ];
      }
      
      public drop(index)
      {
          return [ 
                          type: 'drop_index', 
                           ddl: "drop index ${oid(index.'@name')}",
                   privMessage: "You need to: grant drop index to ${username}" 
                 ];
      }
      
      public detectChanges(source,target)
      {
          def changes= [];
          
          def recreate_index=          
          {
              if (source) 
                changes << drop(source)
              else // constraint index ?
                changes << drop(target)

              changes << create(target)
          }

          if (  !cmp(source,target,'table')
              ||!cmp(source,target,'unique','false')
              ||!cmp(source,target,'bitmap','false')
              || source.columns.column.size()!=target.columns.column.size())
            recreate_index();
          else
          {
              def equals= true;
              
              source.columns.column.eachWithIndex
              {
                    sourceCol, index -> 
                    
                    def targetCol= target.columns.column[index]; 
                    
                    if (  !cmp(indexPart(sourceCol),indexPart(targetCol))
                        ||!cmp(sourceCol,targetCol,'direction','asc'))
                    {
                      equals= false
                      return
                    }
              }
              
              if (!equals)
                recreate_index();
          }
          
          return changes
      }
      
      private indexPart(col)
      {
          return (col.'@name' ? oid(col.'@name') : col.'@expression');
      }
}
