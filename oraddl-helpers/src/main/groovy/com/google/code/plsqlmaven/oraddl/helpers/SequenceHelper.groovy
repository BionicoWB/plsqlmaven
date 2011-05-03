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
              def cache= rd(seq.cache_size,20);
              xml.sequence('name':         xid(seq.sequence_name), 
                           'min-value':    rd(seq.min_value,1),
                           'max-value':    rd(seq.max_value,999999999999999999999999999),
                           'increment-by': rd(seq.increment_by,1),
                           'cache':        (cache=='0' ? 'false' : cache),
                           'cycle':        (seq.cycle_flag=='Y' ? 'true' : null),
                           'order':        (seq.order_flag=='Y' ? 'true' : null));
           }
           
           return true;
      }
      
      public boolean exists(sequence)
      {
           def exists= false;
           sql.eachRow("select 1 from user_sequences where sequence_name= upper(${oid(sequence.'@name',false)})")
           { exists= true }
           
           return exists;
      }
      
      public create(sequence)
      {
          def ddl= 'create sequence '+oid(sequence.'@name');
          
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
            if (sequence.'@cache'=='false'||sequence.'@cache'=='0')
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
          
          return [    
                           type: 'create_sequence',
                            ddl: ddl, 
                    privMessage: "You need to: grant create sequence to ${username}" 
                 ]
      }
      
      public detectChanges(source,target)
      {
          def changes= []
          
          if (!cmp(source,target,'min-value',1))
            changes << sequence_minvalue(target)
             
          if (!cmp(source,target,'max-value',999999999999999999999999999))
            changes << sequence_maxvalue(target)
           
          if (!cmp(source,target,'increment-by',1))
            changes << sequence_incrementby(target);
             
          if (!cmp(source,target,'cache',20))
            changes << sequence_cache(target);
           
          if (!cmp(source,target,'cycle','false'))
            changes << sequence_cycle(target);
           
          if (!cmp(source,target,'order','false'))
            changes << sequence_order(target);
          
          return changes
      }
   
      /*   CHANGES    */
      
      public sequence_minvalue(sequence)
      {
          def ddl;
          
          if (sequence.'@min-value'!=null)
            ddl= "alter sequence ${oid(sequence.'@name')} minvalue ${sequence.'@min-value'}"
          else
            ddl= "alter sequence ${oid(sequence.'@name')} nominvalue"
            
          return [
                            type: 'sequence_minvalue',
                             ddl: ddl,
                     privMessage: "You need to: grant alter sequence to ${username}"
                 ]
   
      }
   
      public sequence_maxvalue(sequence)
      {
          def ddl;
   
          if (change.maxvalue!=null)
            ddl= "alter sequence ${oid(sequence.'@name')} maxvalue ${sequence.'@max-value'}"
          else
            ddl= "alter sequence ${oid(sequence.'@name')} nomaxvalue"
   
          return [
                            type: 'sequence_maxvalue',
                             ddl: ddl,
                     privMessage: "You need to: grant alter sequence to ${username}"
                 ]
      }
   
      public sequence_incrementby(sequence)
      {
          def ddl;
   
          if (sequence.'@increment-by'!=null)
            ddl= "alter sequence ${oid(sequence.'@name')} increment by ${sequence.'@increment-by'}"
          else
            ddl= "alter sequence ${oid(sequence.'@name')} increment by 1"
            
          return [
                            type: 'sequence_incrementby',
                             ddl: ddl,
                     privMessage: "You need to: grant alter sequence to ${username}"
                 ]
      }
   
      public sequence_cache(sequence)
      {
          def ddl;
          
          if (sequence.'@cache'!=null)
          {
            if (sequence.'@cache'=='false'||sequence.'@cache'=='0')
              ddl= "alter sequence ${oid(sequence.'@name')} nocache"
            else
              ddl= "alter sequence ${oid(sequence.'@name')} cache ${sequence.'@cache'}"
          }
          else
            ddl= "alter sequence ${oid(sequence.'@name')} cache 20"
            
          return [
                            type: 'sequence_cache',
                             ddl: ddl,
                     privMessage: "You need to: grant alter sequence to ${username}"
                 ]
      }
   
      public sequence_order(sequence)
      {
          def ddl;
          
          if (sequence.'@order'!=null)
          {
            if (sequence.'@order'=='false')
              ddl= "alter sequence ${oid(sequence.'@name')} noorder"
            else
              ddl= "alter sequence ${oid(sequence.'@name')} order"
          }
          else
            ddl= "alter sequence ${oid(sequence.'@name')} noorder"
            
          return [
                            type: 'sequence_order',
                             ddl: ddl,
                     privMessage: "You need to: grant alter sequence to ${username}"
                 ]
      }
   
      public sequence_cycle(sequence)
      {
          def ddl;
          
          if (sequence.'@cycle'!=null)
          {
            if (sequence.'@cycle'=='false')
              ddl= "alter sequence ${oid(sequence.'@name')} nocycle"
            else
              ddl= "alter sequence ${oid(sequence.'@name')} cycle"
          }
          else
            ddl= "alter sequence ${oid(sequence.'@name')} nocycle"
   
          return [
                            type: 'sequence_cycle',
                             ddl: ddl,
                     privMessage: "You need to: grant alter sequence to ${username}"
                 ]
      }
      
   
}
