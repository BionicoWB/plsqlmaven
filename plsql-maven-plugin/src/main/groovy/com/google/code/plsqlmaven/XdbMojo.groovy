package com.google.code.plsqlmaven;

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

import java.io.File;


/**
 * Abstract class for XDB related Mojos
 *
 */
public abstract class XdbMojo
    extends PlSqlMojo
{

   /**
    * The base path from witch the export should start
    * @since 1.0
    * @parameter expression="${basePath}"
    * @required
    */
   protected String basePath;

   /**
    * Source directory for XDB files src/main/xdb
    */
   public String getXdbSourceDirectory()
   {
       return project.basedir.absolutePath+File.separator+"src"+File.separator+"main"+File.separator+"xdb"+File.separator;
   }   
   

}
