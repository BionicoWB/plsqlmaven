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
                                'length':        rd(col.char_length,0));
                  }
              }
          }
          
          return true;
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
          def cols='';
          
          table.columns.column.each
          {
              col ->
              cols+=','+col.'@name'+' '+getColumnType(col)
          }
          
          doddl('create table '+(table.'@name')+'('+cols.substring(1)+')',
                "You need to: grant create table to ${username}")
      }
      
      public List detectChanges(table)
      {
          def changes= [];
          def cnt= 1;
          
          def tableName= table.'@name'
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
              if (dataType!=col.'@type'
                  ||!cmp(dbcol.data_precision,col.'@precision')
                  ||!cmp(dbcol.data_scale,col.'@scale')
                  ||!cmp(dbcol.char_length,dv(col.'@length',0)))
                changes << [type: 'modify_column', column: columnName, columnType: getColumnType(col), table: tableName]
          }
          
          changes+= table.columns.column.findAll{ c -> (!(c.'@name' in existingCols))}
                                        .collect{ c -> [type:       'add_column',
                                                        column:     c.'@name',
                                                        columnType: getColumnType(c),
                                                        table:      tableName] }
          
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
   
}
