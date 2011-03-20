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

class SynonymHelper extends OraDdlHelper
{
      public SynonymHelper(sql,log,username)
      {
          super(sql,log,username);
      }

      public boolean extract(name,xml)
      {
           sql.eachRow("select a.*, user current_user from user_synonyms a where synonym_name = upper(${name})")
           {
              def syn= it.toRowResult()
              xml.sequence('name':         name, 
                           'for':          syn.table_name.toLowerCase(),
                           'for-owner':    rd(syn.table_owner,syn.current_user)?.toLowerCase(),
                           'db-link':      syn.db_link?.toLowerCase());
           }
           
           return true;
      }
      
      public boolean exists(synonym)
      {
           def exists= false;
           sql.eachRow("select 1 from user_synonyms where synonym_name= upper(${synonym.'@name'})")
           { exists= true }
           
           return exists;
      }
   
      public create(synonym)
      {
          def ddl= "create synonym ${synonym.'@name'} for "
          
          if (synonym.'@for-owner')
            ddl+= synonym.'@for-owner'+'.'
            
          ddl+= synonym.'@for'
   
          if (synonym.'@db-link')
            ddl+= '@'+synonym.'@db-link'
   
          doddl(ddl,"You need to: grant create synonym to ${username}")
      }
      
      public List detectChanges(synonym)
      {
          def changes= []
          
          sql.eachRow("select a.*, user current_user from user_synonyms a where synonym_name= upper(${synonym.'@name'})")
          {
             def dbsyn= it.toRowResult()
             
             if (!cmp(dv(synonym.'@for-owner',dbsyn.current_user.toLowerCase()),dbsyn.table_owner.toLowerCase())
               ||!cmp(synonym.'@for',dbsyn.table_name.toLowerCase())
               ||!cmp(synonym.'@db-link',dbsyn.db_link?.toLowerCase()))
               changes << [type: 'synonym_change', synonym: synonym]
          }
          
          return changes
      }
      
      /*   CHANGES    */
      
      public synonym_change(change)
      {
            doddl("drop synonym ${change.synonym.'@name'}",
                   "You need to: grant drop synonym to ${username}")
            
            create(change.synonym)
      }
   
}
