package com.google.code.plsqlmaven.oraddl

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

import groovy.xml.MarkupBuilder

/**
 * Extracts schema objects to xml files
 *
 * @goal sync
 */
public class OraDdlSyncMojo
    extends OraDdlMojo
{
  /**
   * Sync only changed (from last sync) schema files
   * @since 1.0
   * @parameter expression="${changedOnly}"
   */
   private boolean changedOnly;

  /**
   * Detect changes but don't apply it
   * @since 1.0
   * @parameter expression="${detectOnly}"
   */
   private boolean detectOnly;

   void execute()
   {
       if (!connectToDatabase())
       {
         fail('Need an Oracle connection')
         return
       }
         
       syncObjects()
       disconnectFromDatabase()
   }
   
   private syncObjects()
   {
       def scanner=  ant.fileScanner
       {
           fileset(dir: sourceDirectory)
           {
               include(name: '**/*.xml')
           }
       }

       for (file in scanner)
       {
           def path= file.absolutePath.split(File.separator)
           def type= path[path.length-2]
           def name= path[path.length-1].split('\\.')[0]
           log.info "sync ${type} ${name}"
           "sync_${type}"(file);
       }
   } 
   
   private sync_table(file)
   {
       def table = new XmlParser().parse(file)
       
       if (tableExists(table))
         applyChanges(detectTableChanges(table))
       else
         createTable(table)       
   }
   
   private sync_index(file)
   {
       def index = new XmlParser().parse(file)
       
       if (tableExists(index))
         applyChanges(detectIndexChanges(index))
       else
         createIndex(index)
   }

   private sync_sequence(file)
   {
       def sequence = new XmlParser().parse(file)
       
       if (tableExists(sequence))
         applyChanges(detectSequenceChanges(sequence))
       else
         createSequence(sequence)
   }

   private sync_synonym(file)
   {
       def synonym = new XmlParser().parse(file)
       
       if (tableExists(synonym))
         applyChanges(detectSynonymChanges(synonym))
       else
         createSynonym(synonym)
   }
   
   private applyChanges(changes)
   {
       if (log.debugEnabled)
         changes.each{ log.debug it.toString() }

       if (!detectOnly)
           changes.each
           {
               change ->
               
               "${change.type}"(change);
           }
   }
   
   private drop_column(change)
   {
       sql.execute("alter table ${change.table} drop column ${change.column}".toString());
   }

   private modify_column(change)
   {
       sql.execute("alter table ${change.table} modify (${change.column} ${change.columnType})".toString());
   }

   private add_column(change)
   {
       sql.execute("alter table ${change.table} add (${change.column} ${change.columnType})".toString());
   }
   
   private sequence_minvalue(change)
   {
       if (change.minvalue!=null)
         sql.execute("alter sequence ${change.sequence} minvalue ${change.minvalue}".toString());
       else
         sql.execute("alter sequence ${change.sequence} nominvalue".toString());
   }

   private sequence_maxvalue(change)
   {
       if (change.maxvalue!=null)
         sql.execute("alter sequence ${change.sequence} maxvalue ${change.maxvalue}".toString());
       else
         sql.execute("alter sequence ${change.sequence} nomaxvalue".toString());
   }

   private sequence_incrementby(change)
   {
       if (change.incrementby!=null)
         sql.execute("alter sequence ${change.sequence} increment by ${change.incrementby}".toString());
       else
         sql.execute("alter sequence ${change.sequence} increment by 1".toString());
   }

   private sequence_cache(change)
   {
       if (change.cache!=null)
       {
         if (change.cache=='false')
           sql.execute("alter sequence ${change.sequence} nocache".toString());
         else
           sql.execute("alter sequence ${change.sequence} cache ${change.cache}".toString());
       }
       else
         sql.execute("alter sequence ${change.sequence} cache 20".toString());
   }

   private sequence_order(change)
   {
       if (change.order!=null)
       {
         if (change.order=='false')
           sql.execute("alter sequence ${change.sequence} noorder".toString());
         else
           sql.execute("alter sequence ${change.sequence} order".toString());
       }
       else
         sql.execute("alter sequence ${change.sequence} noorder".toString());
   }

   private sequence_cycle(change)
   {
       if (change.cycle!=null)
       {
         if (change.cycle=='false')
           sql.execute("alter sequence ${change.sequence} nocycle".toString())
         else
           sql.execute("alter sequence ${change.sequence} cycle".toString())
       }
       else
         sql.execute("alter sequence ${change.sequence} nocycle".toString())
   }
   
   private synonym_change(change)
   {
         sql.execute("drop synonym ${change.synonym.'@name'}".toString())
         createSynonym(change.synonym)
   }
   
   private index_change(change)
   {
         sql.execute("drop index ${change.index.'@name'}".toString())
         createIndex(change.index)
   }

   private indexExists(index)
   {
        def exists= false;
        sql.eachRow("select 1 from user_indexes where index_name= upper(${index.'@name'})")
        { exists= true }
        
        return exists;
   }
   
   private createIndex(index)
   {
       def ddl= "create"+(index.'@unique'=='true' ? ' unique' : '')
               +" index ${index.'@name'} on "+index.'@table'+"("+index.columns.column*.'@name'.join(',')+")"
       log.debug ddl
       sql.execute ddl.toString();
   }
   
   private detectIndexChanges(index)
   {
       def changes= [];
       def cnt= 0;
       
       def indexName= index.'@name'
       def existingCols= []
       
       sql.eachRow("""select column_name,
                             column_expression,
                             descend
                        from user_ind_columns a,
                             user_ind_expressions b
                       where b.column_position(+)= a.column_position
                         and b.index_name(+)= a.index_name
                         and a.index_name= upper(${name})
                    order by column_position""")
       {
           def dbcol= it.toRowResult()
           def col= index.columns.column[cnt++]
           if (!col
               ||col.'@name'!= val(dbcol.column_expression,dbcol.column_name)
               ||val(col.'@direction','asc')!=val(dbcol.descend,'asc')?.toLowerCase())
           {
               changes << [type: 'index_change', index: index]
               return;
           }
       }
       
       // column added
       if (index.columns.column[cnt++])
         changes << [type: 'index_change', index: index]
       
       return changes
   }

   private synonymExists(synonym)
   {
        def exists= false;
        sql.eachRow("select 1 from user_synonyms where synonym_name= upper(${synonym.'@name'})")
        { exists= true }
        
        return exists;
   }

   private createSynonym(synonym)
   {
       def ddl= "create synonym ${synonym.'@name'} for "
       
       if (synonym.'@for-owner')
         ddl+= synonym.'@for-owner'+'.'
         
       ddl+= synonym.'@for'

       if (synonym.'@db-link')
         ddl+= '@'+synonym.'@db-link'

       log.debug ddl
       sql.execute ddl.toString();
   }
   
   private detectSynonymChanges(synonym)
   {
       def changes= []
       
       sql.eachRow("select a.*, user current_user from user_synonym a where synonym_name= upper(${synonym.'@name'})")
       {
          def dbsyn= it.toRowResult()
          
          if (!cmp(val(synonym.'@for-owner',dbsyn.current_user.toLowerCase()),dbsyn.table_owner.toLowerCase())
            ||!cmp(synonym.'@for',dbsyn.table_name.toLowerCase())
            ||!cmp(synonym.'@db-link',dbsyn.db_link?.toLowerCase()))
            changes << [type: 'synonym_change', synonym: synonym]
       }
       
       return changes
   }

   private sequenceExists(sequence)
   {
        def exists= false;
        sql.eachRow("select 1 from user_sequences where sequence_name= upper(${sequence.'@name'})")
        { exists= true }
        
        return exists;
   }
   
   private createSequence(sequence)
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
       
       log.debug ddl
       sql.execute ddl
   }
   
   private detectSequenceChanges(sequence)
   {
       def changes= []
       
       sql.eachRow("select * from user_sequences where sequence_name= upper(${sequence.'@name'})")
       {
          def dbseq= it.toRowResult()
                   
          if (!cmp(val(sequence.'@min-value',1),val(dbseq.min_value,1)))
              changes << [type: 'sequence_minvalue', sequence: sequence.'@name', minvalue:  sequence.'@min-value']
              
          if (!cmp(val(sequence.'@max-value',999999999999999999999999999),val(dbseq.max_value,999999999999999999999999999)))
              changes << [type: 'sequence_maxvalue', sequence: sequence.'@name', maxvalue:  sequence.'@max-value']

          if (!cmp(val(sequence.'@increment-by',1),val(dbseq.increment_by,1)))
              changes << [type: 'sequence_incrementby', sequence: sequence.'@name', incrementby:  sequence.'@increment-by']
              
          if (!cmp(val(sequence.'@cache',20),val(dbseq.cache_size,20)))
              changes << [type: 'sequence_cache', sequence: sequence.'@name', 'cache':  sequence.'@cache']

          if (!cmp(sequence.'@cycle',(seq.cycle_flag=='Y' ? 'true' : null)))
              changes << [type: 'sequence_cycle', sequence: sequence.'@name', 'cycle':  sequence.'@cycle']

          if (!cmp(sequence.'@order',(seq.order_flag=='Y' ? 'true' : null)))
              changes << [type: 'sequence_order', sequence: sequence.'@name', 'order':  sequence.'@order']
       }
       
       return changes
   }

   private tableExists(table)
   {
        def exists= false;
        sql.eachRow("select 1 from user_tables where table_name= upper(${table.'@name'})")
        { exists= true }
        
        return exists
   }
    
   private createTable(table)
   {
       def cols='';
       
       table.columns.column.each
       {
           col ->
           cols+=','+col.'@name'+' '+getColumnType(col)
       }
       
       def ddl= 'create table '+(table.'@name')+'('+cols.substring(1)+')'
       log.debug ddl
       sql.execute ddl;
   }
   
   private detectTableChanges(table)
   {
       def changes= [];
       def cnt= 1;
       
       def tableName= table.'@name'
       def existingCols= []
       
       sql.eachRow("select * from user_tab_columns where table_name = upper(${tableName}) order by column_id")
       {
           def dbcol= it.toRowResult()
           def columnName= dbcol.column_name.toLowerCase()
           def col= table.columns.column.find({ col -> col.'@name'== columnName })
           def p= dbcol.data_type.indexOf('(')
           def dataType= dbcol.data_type.toLowerCase().substring(0,(p==-1?dbcol.data_type.length():p))
           existingCols << columnName
                      
           if (!col)
             changes << [type: 'drop_column', column: columnName, table: tableName]
           else  
           if (dataType!=col.'@type'
               ||!cmp(dbcol.data_precision,col.'@precision')
               ||!cmp(dbcol.data_scale,col.'@scale')
               ||!cmp(dbcol.char_length,val(col.'@length',0)))
             changes << [type: 'modify_column', column: columnName, columnType: getColumnType(col), table: tableName]
       }
       
       changes+= table.columns.column.findAll{ c -> (!(c.'@name' in existingCols))}
                                     .collect{ c -> [type:       'add_column', 
                                                     column:     c.'@name', 
                                                     columnType: getColumnType(c), 
                                                     table:      tableName] }
       
       return changes
   }
   
   private getColumnType(col)
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
   
   private val(v,d)
   {
       return (v ? v : d);
   }

   private cmp(v1,v2)
   {
       return (v1?.toString()==v2?.toString());
   }
}
