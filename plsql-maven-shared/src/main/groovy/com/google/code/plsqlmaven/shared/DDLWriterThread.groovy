package com.google.code.plsqlmaven.shared

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

/**
 * Writes a DDL file using changes produced by various mojos
 * @plexus.component role="com.google.code.plsqlmaven.shared.DDLWriterThread"
 */
class DDLWriterThread extends Thread
{
       private instances= [:];

       def ant= new AntBuilder();
       def log,ddl
       def mojos= []

       public getInstance(dir,fileName)
       {
            def instance 

            def finalPath= dir+File.separator+fileName

            if ((instance=instances[finalPath])==null)
            {
              instances[finalPath]= instance= new DDLWriterThread()
              instance.writeTo(dir,fileName)
              Runtime.getRuntime().addShutdownHook(instance)
            }

            return instance
       }

       public DDLWriterThread()
       {}

       public void writeTo(dir,fileName)
       {
              ant.mkdir(dir: dir)
              ddl= new File(dir,fileName);
//              ant.truncate(file: ddl.absolutePath);
       }

       public void registerMojo(mojo)
       {
           log= mojo.log
           mojos << mojo
       }
       
       public void run()
       {
           def changes= []
           mojos.each{ changes += it.changes }
           mojos.each{ changes = it.reorder(changes) }

           log.info "final changes: "+changes.size()
           
           if (changes.size()>0)
           {
               log.info "generating DDL ${ddl.absolutePath}..."
               
               changes.each
               { 
                   change ->
                   
                   ddl << mlc(change.failSafe ? makeFailSafe(change.ddl) : change.ddl)+"\n/\n\n"
                    
               }
           }
       }
       
       private makeFailSafe(ddl)
       {
           def ddlEscaped= ddl.replaceAll("'","''");
           return mlc("""-- failsafe ddl
                         begin
                          execute immediate '${ddlEscaped}';
                         exception
                           when others then
                             null;
                         end;""")
       }
	   
      public mlc(multiLineText)
      {   
		  if (multiLineText.indexOf("\n")==-1)
		    return multiLineText
			
		  def torm= (multiLineText =~ '(?m)^ *')
		  
		  if (torm.size()>1)
		    return multiLineText.replaceAll('(?m)^ *','')
      }

}
