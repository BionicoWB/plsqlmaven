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


/**
 * Packages the PL/SQL artifact
 *
 * @goal xdb-package
 *
 * @phase prepare-package
 */
public class XdbPackageMojo
    extends XdbMojo
{
    
    void execute()
    {
        def xdbOutputDirectory= project.build.outputDirectory+File.separator+"xdb"
        if (project.getPackaging()=='war')
          xdbOutputDirectory= project.build.directory+"/${project.build.finalName}/WEB-INF/xdb".replace('/', File.separator)

        ant.mkdir(dir: xdbOutputDirectory)
        ant.copy(todir: xdbOutputDirectory)
        {
            ant.fileset(dir: xdbSourceDirectory)
        }
    }
}