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

class SequenceHelper extends OraDdlHelper
{
      public SequenceHelper(sql,log,username)
      {
          super(sql,log,username);
      }

      public boolean extract(name,xml)
      {
           sql.eachRow("select * from user_sequences where sequence_name = upper(${name})")
           {
              def seq= it.toRowResult()
              xml.sequence('name':         name, 
                           'min-value':    rd(seq.min_value,1),
                           'max-value':    rd(seq.max_value,999999999999999999999999999),
                           'increment-by': rd(seq.increment_by,1),
                           'cache':        rd(seq.cache_size,20),
                           'cycle':        (seq.cycle_flag=='Y' ? 'true' : null),
                           'order':        (seq.order_flag=='Y' ? 'true' : null));
           }
           
           return true;
      }
      
      public boolean exists(sequence)
      {
           def exists= false;
           sql.eachRow("select 1 from user_sequences where sequence_name= upper(${sequence.'@name'})")
           { exists= true }
           
           return exists;
      }
      
      public create(sequence)
      {
          def ddl= 'create sequence '+(sequence.'@name');
          
          if (sequence.'@min-value'!=null)
            ddl+=' minvalue '+sequence.'@min-value'
          
          if (sequence.'@start-with'!=null)
            ddl+=' start with '+sequence.'@start-with'
   
          if (sequence.'@max-value'!=null)
            ddl+=' maxvalue '+sequence.'@max-value'
   
          if (sequence.'@increment-by'!=null)
            ddl+=' increment by '+sequence.'@increment-by'
            
          if (sequence.'@cache'!=null)
          {
            if (sequence.'@cache'=='false')
               ddl+=' nocache'
            else
               ddl+=' cache '+sequence.'@cache'
          }
          
          if (sequence.'@order'!=null)
          {
            if (sequence.'@order'=='false')
              ddl+=' noorder'
            else
              ddl+=' order'
          }
          
          if (sequence.'@cycle'!=null)
          {
            if (sequence.'@cycle'=='false')
              ddl+=' nocycle'
            else
              ddl+=' cycle'
          }
          
          doddl(ddl,"You need to: grant create sequence to ${username}")
      }
      
      public List detectChanges(sequence)
      {
          def changes= []
          
          sql.eachRow("select * from user_sequences where sequence_name= upper(${sequence.'@name'})")
          {
             def dbseq= it.toRowResult()
                      
             if (!cmp(dv(sequence.'@min-value',1),dv(dbseq.min_value,1)))
                 changes << [type: 'sequence_minvalue', sequence: sequence.'@name', minvalue:  sequence.'@min-value']
                 
             if (!cmp(dv(sequence.'@max-value',999999999999999999999999999),dv(dbseq.max_value,999999999999999999999999999)))
                 changes << [type: 'sequence_maxvalue', sequence: sequence.'@name', maxvalue:  sequence.'@max-value']
   
             if (!cmp(dv(sequence.'@increment-by',1),dv(dbseq.increment_by,1)))
                 changes << [type: 'sequence_incrementby', sequence: sequence.'@name', incrementby:  sequence.'@increment-by']
                 
             if (!cmp(dv(sequence.'@cache',20),dv(dbseq.cache_size,20)))
                 changes << [type: 'sequence_cache', sequence: sequence.'@name', 'cache':  sequence.'@cache']
   
             if (!cmp(sequence.'@cycle',(dbseq.cycle_flag=='Y' ? 'true' : null)))
                 changes << [type: 'sequence_cycle', sequence: sequence.'@name', 'cycle':  sequence.'@cycle']
   
             if (!cmp(sequence.'@order',(dbseq.order_flag=='Y' ? 'true' : null)))
                 changes << [type: 'sequence_order', sequence: sequence.'@name', 'order':  sequence.'@order']
          }
          
          return changes
      }
   
      /*   CHANGES    */
      
      public sequence_minvalue(change)
      {
          def ddl;
          
          if (change.minvalue!=null)
            ddl= "alter sequence ${change.sequence} minvalue ${change.minvalue}"
          else
            ddl= "alter sequence ${change.sequence} nominvalue"
            
          doddl(ddl, "You need to: grant alter sequence to ${username}")
   
      }
   
      public sequence_maxvalue(change)
      {
          def ddl;
   
          if (change.maxvalue!=null)
            ddl= "alter sequence ${change.sequence} maxvalue ${change.maxvalue}"
          else
            ddl= "alter sequence ${change.sequence} nomaxvalue"
   
          doddl(ddl, "You need to: grant alter sequence to ${username}")
      }
   
      public sequence_incrementby(change)
      {
          def ddl;
   
          if (change.incrementby!=null)
            ddl= "alter sequence ${change.sequence} increment by ${change.incrementby}"
          else
            ddl= "alter sequence ${change.sequence} increment by 1"
            
          doddl(ddl, "You need to: grant alter sequence to ${username}")
      }
   
      public sequence_cache(change)
      {
          def ddl;
          
          if (change.cache!=null)
          {
            if (change.cache=='false')
              ddl= "alter sequence ${change.sequence} nocache"
            else
              ddl= "alter sequence ${change.sequence} cache ${change.cache}"
          }
          else
            ddl= "alter sequence ${change.sequence} cache 20"
            
          doddl(ddl, "You need to: grant alter sequence to ${username}")
      }
   
      public sequence_order(change)
      {
          def ddl;
          
          if (change.order!=null)
          {
            if (change.order=='false')
              ddl= "alter sequence ${change.sequence} noorder"
            else
              ddl= "alter sequence ${change.sequence} order"
          }
          else
            ddl= "alter sequence ${change.sequence} noorder"
            
          doddl(ddl, "You need to: grant alter sequence to ${username}")
      }
   
      public sequence_cycle(change)
      {
          def ddl;
          
          if (change.cycle!=null)
          {
            if (change.cycle=='false')
              ddl= "alter sequence ${change.sequence} nocycle"
            else
              ddl= "alter sequence ${change.sequence} cycle"
          }
          else
            ddl= "alter sequence ${change.sequence} nocycle"
   
          doddl(ddl, "You need to: grant alter sequence to ${username}")
      }
      
   
}
