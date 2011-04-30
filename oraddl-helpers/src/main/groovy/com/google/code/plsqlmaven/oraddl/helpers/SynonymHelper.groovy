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
              xml.synonym('name':         name, 
                          'for':          syn.table_name.toLowerCase(),
                          'for-owner':    rd(syn.table_owner,syn.current_user)?.toLowerCase(),
                          'db-link':      syn.db_link?.toLowerCase())
           }
           
           return true
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
   
          return [ 
                          type: 'create_synonym',
                           ddl: ddl,
                   privMessage: "You need to: grant create synonym to ${username}"
                 ]
      }
      
      public drop(synonym)
      {
          return [ 
                          type: 'drop_synonym',
                           ddl: "drop synonym ${synonym.'@name'}",
                   privMessage: "You need to: grant drop synonym to ${username}"
                 ]
      }

      public List detectChanges(source,target)
      {
          def changes= []
          
          if (!cmp(source,target,'for-owner',username)
            ||!cmp(source,target,'for')
            ||!cmp(source,target,'db-link'))
          {
            changes << drop(source)
            changes << create(target)
          }
             
          return changes
      }
      
}
