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

class MaterializedViewHelper extends OraDdlHelper
{
      public MaterializedViewHelper(sql,log,username)
      {
          super(sql,log,username);
      }

      public boolean extract(name,xml)
      {
           sql.eachRow("select a.*, b.prebuilt, dbms_metadata.get_ddl('MATERIALIZED_VIEW',upper(${name})) ddl from user_mviews a, user_snapshots b where b.name= a.mview_name and a.mview_name = upper(${name})")
           {
              def mview= it.toRowResult()
              xml.mview('name':                 xid(mview.mview_name),
                        'updatable':            (mview.updatable=='N' ? null : 'true'),
                        'refresh-mode':         (mview.refresh_mode=='DEMAND' ? null : mview.refresh_mode?.toLowerCase()),
                        'build-mode':           (mview.build_mode=='DEFERRED' ? 'deferred' : null),
                        'refresh-method':       (mview.refresh_method=='FORCE' ? null : mview.refresh_method?.toLowerCase()),
                        'prebuilt-table':       (mview.prebuilt=='NO' ? null : 'true'),
                        'reduced-precision':    reducedPrecision(mview.ddl)
                       )
              {
                  xml.columns()
                  {
                      sql.eachRow("select * from user_tab_columns a where table_name = upper(${name}) order by column_id")
                      {
                         def col= it.toRowResult()
                         
                         xml.column('name': xid(col.column_name))
                      }
                  }
    
                 xml.text('') 
                 {
                      out.print("<![CDATA[${mview.query}]]>")
                 }
              }
           }
           
           return true
      }
      
      public boolean exists(mview)
      {
           def exists= false;
           sql.eachRow("select 1 from user_mviews where mview_name= upper(${oid(mview.'@name',false)})")
           { exists= true }
           
           return exists;
      }
   
      public create(mview)
      {
          def ddl= "create materialized view ${oid(mview.'@name')} ("+mview.columns.column.collect{ col-> oid(col.'@name') }.join(',')+") "+buildClause(mview)+prebuiltClause(mview)+refreshClause(mview)+" as ${mview.text.text()}"
          
          return [
                          type: 'create_mview',
                           ddl: ddl,
                   privMessage: "You need to: grant create materialized view to ${username}"
                 ];

      }
      
      public drop(mview)
      {
          return [
                          type: 'drop_mview',
                           ddl: "drop view ${oid(mview.'@name')}",
                   privMessage: "You need to: grant drop materialized view to ${username}"
                 ];

      }

      public detectChanges(source,target)
      {
          def changes= []
          
          def recreate_mview=
          {
              changes << drop(source)
              changes << create(target)
          }
          
          
          if (!cmp(source.text.text(),target.text.text())
              || !cmp(source,target,'updatable')
              || !cmp(source,target,'refresh-mode')
              || !cmp(source,target,'build-mode')
              || !cmp(source,target,'refresh-method')
              || !cmp(source,target,'prebuilt-table')
              || source.columns.column.size()!=target.columns.column.size())
            recreate_mview();
          else
          {
              def equals= true;
              
              source.columns.column.eachWithIndex
              {
                    sourceCol, index -> 

                    def targetCol= target.columns.column[index]
                    
                    if (!cmp(sourceCol,targetCol,'name'))
                    {
                      equals= false
                      return
                    }
              }
              
              if (!equals)
                recreate_mview();
          }
          
          return changes
      }

      private reducedPrecision(ddl)
      {
          return (ddl=~/WITH REDUCED PRECISION/ ? 'true' : null)
      }

      private prebuiltClause(mview)
      {
            def ddl= '';

            if (mview.'@prebuilt-table'=='true')
            {
                ddl+=' on prebuilt table'
                
                if (mview.'@reduced-precision'=='true')
                  ddl+=' with reduced precision'
            }

            return ddl;
      }


      private refreshClause(mview)
      {
            return " refresh "+(mview.'@refresh-method' ? mview.'@refresh-method' : 'force')+" on "+(mview.'@refresh-mode' ? mview.'@refresh-mode' : 'demand');
      }
     
      private buildClause(mview)
      {
            def ddl= '';

            if (mview.'@build-mode')
             ddl= " build ${mview.'@build-mode'}";

            return ddl;
      }
      
}
