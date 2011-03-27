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

/**
 * Base class for ddl helpers
 */
abstract class OraDdlHelper
{
      protected Sql sql;
      protected log;
      protected username;
      
      public OraDdlHelper(sql,log,username)
      {
          this.sql= sql;
          this.log= log;
          this.username= username;
      }
      
      public rd(v,d)
      {
          return (v==d ? null : v);
      }
      
      public dv(v,d)
      {
          return (v ? v : d);
      }
      
      public cmp(v1,v2)
      {
          log.debug 'cmp: '+v1?.toString()+'=='+v2?.toString()
          return (v1?.toString()==v2?.toString());
      }
   
      public doddl(ddl,privMessage)
      {
          log.info ddl
          
          try
          {
            sql.execute ddl.toString()
          }
          catch (Exception ex)
          {
              if (ex.errorCode==1031)
                fail(privMessage);
              else
                throw ex;
          }
      }
      
      public getColumnType(col)
      {
          def type=col.'@type'
          def data_length=''
          
          if (col.'@precision'&&col.'@scale')
           data_length= col.'@precision'+','+col.'@scale'
          else
          if (col.'@precision')
           data_length= col.'@precision'
          else
          if (col.'@scale')
           data_length= col.'@scale'
          else
          if (col.'@length')
           data_length= col.'@length'
   
          if (data_length)
           type+='('+data_length+')'
   
          return type
      }
      
      public abstract boolean extract(name,xml);

      public abstract boolean exists(xml);
      
      public abstract List detectChanges(xml);
      
      public abstract create(xml);
}
