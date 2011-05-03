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
      
      public cmp(source,target,attr=null,dval=null)
      {
          def v1= dv((attr ? source."@${attr}" : source),dval)
          def v2= dv((attr ? target."@${attr}" : target),dval)
          
          log.debug "@${attr} cmp: "+v1?.toString()+'=='+v2?.toString()
          return (v1?.toString()==v2?.toString());
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
           data_length= (col.'@type'!='timestamp' ? '*,' : '')+col.'@scale'
          else
          if (col.'@length')
           data_length= col.'@length'
   
          if (data_length)
           type+='('+data_length+')'
           
          if (col.'@default')
           type+=' default '+col.'@default'
   
          return type
      }
      
      public oid(xmlIdentifier,quote=true)
      {
		  if (xmlIdentifier==null)
		    return null
			
          if (xmlIdentifier.startsWith('!'))
            return (quote ? '"'+xmlIdentifier.substring(1)+'"' : xmlIdentifier.substring(1))
          else
            return xmlIdentifier
      }
      
      public xid(oracleIdentifier)
      {
		  if (oracleIdentifier==null)
		    return null
			
          if (oracleIdentifier!=oracleIdentifier.toUpperCase()
              ||(oracleIdentifier==~'.* .*')
              ||(oracleIdentifier==~'^[^A-Z].*'))
            return '!'+oracleIdentifier
          else
            return oracleIdentifier.toLowerCase()
      }

      public reorder(changes)
      {
          return changes
      }
      
      public abstract boolean extract(name,xml);

      public abstract boolean exists(xml);
      
      public abstract detectChanges(source,target);
      
      public abstract create(xml);
}
