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

class ViewHelper extends OraDdlHelper
{
      public ViewHelper(sql,log,username)
      {
          super(sql,log,username);
      }

      public boolean extract(name,xml)
      {
           sql.eachRow("select * from user_views a where view_name = upper(${name})")
           {
              def view= it.toRowResult()
              xml.view('name':         name)
              {
                 xml.text('') 
                 {
                      out.print("<![CDATA[${view.text}]]>")
                 }
              }
           }
           
           return true
      }
      
      public boolean exists(view)
      {
           def exists= false;
           sql.eachRow("select 1 from user_views where view_name= upper(${view.'@name'})")
           { exists= true }
           
           return exists;
      }
   
      public create(view)
      {
          def ddl= "create view ${view.'@name'} as ${view.text.text()}";
          
          doddl(ddl,"You need to: grant create view to ${username}")
      }
      
      public List detectChanges(view)
      {
          def changes= []
          sql.eachRow("select * from user_views a where view_name = upper(${view.'@name'})")
          {
             def dbview= it.toRowResult()
             
             if (!cmp(dbview.text,view.text))
               changes << [type: 'view_change', view: view]
          }
          
          return changes
      }
      
      /*   CHANGES    */
      
      public view_change(change)
      {
            doddl("drop view ${change.view.'@name'}",
                   "You need to: grant drop view to ${username}")
            
            create(change.view)
      }
   
}
