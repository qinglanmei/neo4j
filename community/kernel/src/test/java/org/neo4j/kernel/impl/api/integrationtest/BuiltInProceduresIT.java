/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api.integrationtest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashSet;
import java.util.Set;

import org.neo4j.collection.RawIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.api.Statement;
import org.neo4j.kernel.api.TokenWriteOperations;
import org.neo4j.kernel.api.exceptions.ProcedureException;
import org.neo4j.kernel.api.schema.SchemaDescriptorFactory;
import org.neo4j.kernel.api.security.AnonymousContext;
import org.neo4j.kernel.api.security.SecurityContext;
import org.neo4j.kernel.internal.Version;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.neo4j.helpers.collection.Iterators.asList;
import static org.neo4j.kernel.api.proc.ProcedureSignature.procedureName;
import static org.neo4j.kernel.api.schema.SchemaDescriptorFactory.forLabel;

public class BuiltInProceduresIT extends KernelIntegrationTest
{
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void listAllLabels() throws Throwable
    {
        // Given
        Statement statement = statementInNewTransaction( AnonymousContext.writeToken() );
        long nodeId = statement.dataWriteOperations().nodeCreate();
        int labelId = statement.tokenWriteOperations().labelGetOrCreateForName( "MyLabel" );
        statement.dataWriteOperations().nodeAddLabel( nodeId, labelId );
        commit();

        // When
        RawIterator<Object[],ProcedureException> stream =
                procedureCallOpsInNewTx().procedureCallRead( procedureName( "db", "labels" ), new Object[0] );

        // Then
        assertThat( asList( stream ), contains( equalTo( new Object[]{"MyLabel"} ) ) );
    }

    @Test
    public void listPropertyKeys() throws Throwable
    {
        // Given
        TokenWriteOperations ops = tokenWriteOperationsInNewTransaction();
        ops.propertyKeyGetOrCreateForName( "MyProp" );
        commit();

        // When
        RawIterator<Object[],ProcedureException> stream = procedureCallOpsInNewTx()
                .procedureCallRead( procedureName( "db", "propertyKeys" ), new Object[0] );

        // Then
        assertThat( asList( stream ), contains( equalTo( new Object[]{"MyProp"} ) ) );
    }

    @Test
    public void listRelationshipTypes() throws Throwable
    {
        // Given
        Statement statement = statementInNewTransaction( AnonymousContext.writeToken() );
        int relType = statement.tokenWriteOperations().relationshipTypeGetOrCreateForName( "MyRelType" );
        long startNodeId = statement.dataWriteOperations().nodeCreate();
        long endNodeId = statement.dataWriteOperations().nodeCreate();
        statement.dataWriteOperations().relationshipCreate( relType, startNodeId, endNodeId );
        commit();

        // When
        RawIterator<Object[],ProcedureException> stream = procedureCallOpsInNewTx()
                .procedureCallRead( procedureName( "db", "relationshipTypes" ), new Object[0] );

        // Then
        assertThat( asList( stream ), contains( equalTo( new Object[]{"MyRelType"} ) ) );
    }

    @Test
    public void listProcedures() throws Throwable
    {
        // When
        RawIterator<Object[],ProcedureException> stream = procedureCallOpsInNewTx()
                .procedureCallRead( procedureName( "dbms", "procedures" ), new Object[0] );

        // Then
        assertThat( asList( stream ), containsInAnyOrder(
                equalTo( new Object[]{"dbms.listConfig",
                        "dbms.listConfig(searchString =  :: STRING?) :: (name :: STRING?, description :: STRING?, " +
                        "value :: STRING?)",
                        "List the currently active config of Neo4j."} ),
                equalTo( new Object[]{"db.constraints", "db.constraints() :: (description :: STRING?)",
                        "List all constraints in the database."} ),
                equalTo( new Object[]{"db.indexes",
                        "db.indexes() :: (description :: STRING?, state :: STRING?, type :: STRING?)",
                        "List all indexes in the database."} ),
                equalTo( new Object[]{"db.awaitIndex",
                        "db.awaitIndex(index :: STRING?, timeOutSeconds = 300 :: INTEGER?) :: VOID",
                        "Wait for an index to come online (for example: CALL db.awaitIndex(\":Person(name)\"))."} ),
                equalTo( new Object[]{"db.awaitIndexes", "db.awaitIndexes(timeOutSeconds = 300 :: INTEGER?) :: VOID",
                        "Wait for all indexes to come online (for example: CALL db.awaitIndexes(\"500\"))."} ),
                equalTo( new Object[]{"db.resampleIndex", "db.resampleIndex(index :: STRING?) :: VOID",
                        "Schedule resampling of an index (for example: CALL db.resampleIndex(\":Person(name)\"))."} ),
                equalTo( new Object[]{"db.resampleOutdatedIndexes", "db.resampleOutdatedIndexes() :: VOID",
                        "Schedule resampling of all outdated indexes."} ),
                equalTo( new Object[]{"db.propertyKeys", "db.propertyKeys() :: (propertyKey :: STRING?)",
                        "List all property keys in the database."} ),
                equalTo( new Object[]{"db.labels", "db.labels() :: (label :: STRING?)",
                        "List all labels in the database."} ),
                equalTo( new Object[]{"db.schema", "db.schema() :: (nodes :: LIST? OF NODE?, relationships :: LIST? " +
                                                   "OF " +
                                                   "RELATIONSHIP?)", "Show the schema of the data."} ),
                equalTo( new Object[]{"db.relationshipTypes", "db.relationshipTypes() :: (relationshipType :: " +
                                                              "STRING?)",
                        "List all relationship types in the database."} ),
                equalTo( new Object[]{"dbms.procedures", "dbms.procedures() :: (name :: STRING?, signature :: " +
                                                         "STRING?, description :: STRING?)",
                        "List all procedures in the DBMS."} ),
                equalTo( new Object[]{"dbms.functions", "dbms.functions() :: (name :: STRING?, signature :: " +
                                                        "STRING?, description :: STRING?)",
                        "List all user functions in the DBMS."} ),
                equalTo( new Object[]{"dbms.components", "dbms.components() :: (name :: STRING?, versions :: LIST? OF" +
                                                         " STRING?, edition :: STRING?)",
                        "List DBMS components and their versions."} ),
                equalTo( new Object[]{"dbms.queryJmx", "dbms.queryJmx(query :: STRING?) :: (name :: STRING?, " +
                                                       "description :: STRING?, attributes :: MAP?)",
                        "Query JMX management data by domain and name." +
                        " For instance, \"org.neo4j:*\""} ),
                equalTo( new Object[]{"db.createLabel", "db.createLabel(newLabel :: STRING?) :: VOID", "Create a label"
                } ),
                equalTo( new Object[]{"db.createProperty", "db.createProperty(newProperty :: STRING?) :: VOID",
                        "Create a Property"
                } ),
                equalTo( new Object[]{"db.createRelationshipType",
                        "db.createRelationshipType(newRelationshipType :: STRING?) :: VOID",
                        "Create a RelationshipType"
                } ),
                equalTo( new Object[]{"db.index.manual.nodes",
                        "db.index.manual.nodes(indexName :: STRING?, query :: ANY?) :: (node :: NODE?, weight :: FLOAT?)",
                        "Search nodes from manual index. Replaces `START n=node:nodes('key:foo*')`"
                } ),
                equalTo( new Object[]{"db.index.manual.seek.nodes",
                        "db.index.manual.seek.nodes(indexName :: STRING?, key :: STRING?, value :: ANY?) :: (node :: " +
                        "NODE?)",
                        "Get node from manual index. Replaces `START n=node:nodes(key = 'A')`"
                } ),
                equalTo( new Object[]{"db.index.manual.relationships",
                        "db.index.manual.relationships(indexName :: STRING?, query :: ANY?) :: (relationship :: " +
                        "RELATIONSHIP?, weight :: FLOAT?)",
                        "Search relationship from manual index. Replaces `START r=relationship:relIndex('key:foo*')`"
                } ),
                equalTo( new Object[]{ "db.index.auto.nodes",
                        "db.index.auto.nodes(query :: ANY?) :: (node :: NODE?, weight :: FLOAT?)",
                        "Search nodes from automatic index. Replaces `START n=node:node_auto_index('key:foo*')`"} ),
                equalTo( new Object[]{ "db.index.auto.seek.nodes",
                        "db.index.auto.seek.nodes(key :: STRING?, value :: ANY?) :: (node :: NODE?)",
                        "Get node from automatic index. Replaces `START n=node:node_auto_index(key = 'A')`"} ),
                equalTo( new Object[]{ "db.index.auto.relationships",
                        "db.index.auto.relationships(query :: ANY?) :: (relationship :: RELATIONSHIP?, weight :: FLOAT?)",
                        "Search relationship from automatic index. Replaces `START r=relationship:relationship_auto_index('key:foo*')`"} ),
                equalTo( new Object[]{ "db.index.auto.seek.relationships",
                        "db.index.auto.seek.relationships(key :: STRING?, value :: ANY?) :: " +
                        "(relationship :: RELATIONSHIP?)",
                        "Get relationship from automatic index. Replaces `START r=relationship:relationship_auto_index(key = 'A')`"} ),
                equalTo( new Object[]{ "db.index.manual.add.node",
                        "db.index.manual.add.node(indexName :: STRING?, node :: NODE?, key :: STRING?, value :: ANY?) :: (success :: BOOLEAN?)",
                        "Add a node to a manual index based on a specified key and value"} ),
                equalTo( new Object[]{ "db.index.manual.add.relationship",
                        "db.index.manual.add.relationship(indexName :: STRING?, relationship :: RELATIONSHIP?, key :: STRING?, value :: ANY?) :: " +
                        "(success :: BOOLEAN?)",
                        "Add a relationship to a manual index based on a specified key and value"} ),
                equalTo( new Object[]{ "db.index.manual.remove.node",
                        "db.index.manual.remove.node(indexName :: STRING?, node :: NODE?, key :: STRING?) :: (success :: BOOLEAN?)",
                        "Remove a node from a manual index with an optional key"} ),
                equalTo( new Object[]{ "db.index.manual.remove.relationship",
                        "db.index.manual.remove.relationship(indexName :: STRING?, relationship :: RELATIONSHIP?, key :: STRING?) :: " +
                        "(success :: BOOLEAN?)",
                        "Remove a relationship from a manual index with an optional key"} ),
                equalTo( new Object[]{ "db.index.manual.drop",
                        "db.index.manual.drop(indexName :: STRING?) :: " +
                        "(type :: STRING?, name :: STRING?, config :: MAP?)",
                        "Remove a manual index - YIELD type,name,config"} ),
                equalTo( new Object[]{ "db.index.manual.forNodes",
                        "db.index.manual.forNodes(indexName :: STRING?) :: " +
                        "(type :: STRING?, name :: STRING?, config :: MAP?)",
                        "Get or create a node manual index - YIELD type,name,config"} ),
                equalTo( new Object[]{ "db.index.manual.forRelationships",
                        "db.index.manual.forRelationships(indexName :: STRING?) :: " +
                        "(type :: STRING?, name :: STRING?, config :: MAP?)",
                        "Get or create a relationship manual index - YIELD type,name,config"} ),
                equalTo( new Object[]{ "db.index.manual.exists.forNodes",
                        "db.index.manual.exists.forNodes(indexName :: STRING?) :: (success :: BOOLEAN?)",
                        "Check if a node manual index exists"} ),
                equalTo( new Object[]{ "db.index.manual.exists.forRelationships",
                        "db.index.manual.exists.forRelationships(indexName :: STRING?) :: (success :: BOOLEAN?)",
                        "Check if a relationship manual index exists"} ),
                equalTo( new Object[]{ "db.index.manual.list",
                        "db.index.manual.list() :: (type :: STRING?, name :: STRING?, config :: MAP?)",
                        "List all manual indexes - YIELD type,name,config"} ),

                equalTo( new Object[]{"db.index.manual.seek.relationships",
                        "db.index.manual.seek.relationships(indexName :: STRING?, key :: STRING?, value :: ANY?) :: " +
                        "(relationship :: RELATIONSHIP?)",
                        "Get relationship from manual index. Replaces `START r=relationship:relIndex(key = 'A')`"
                } ),
                equalTo( new Object[]{"db.index.manual.between",
                        "db.index.manual.between(indexName :: STRING?, in :: NODE?, out :: NODE?, query :: ANY?) :: " +
                                "(relationship :: RELATIONSHIP?, weight :: FLOAT?)",
                        "Search relationship from manual index, starting at the node 'in' and ending at 'out'."
                } ),
                equalTo( new Object[]{"db.index.manual.in",
                        "db.index.manual.in(indexName :: STRING?, in :: NODE?, query :: ANY?) :: " +
                                "(relationship :: RELATIONSHIP?, weight :: FLOAT?)",
                        "Search relationship from manual index, starting at the node 'in'."
                } ),
                equalTo( new Object[]{"db.index.manual.out",
                        "db.index.manual.out(indexName :: STRING?, out :: NODE?, query :: ANY?) :: " +
                                "(relationship :: RELATIONSHIP?, weight :: FLOAT?)",
                        "Search relationship from manual index, ending at the node 'out'."
                } )
        ) );
        commit();
    }

    @Test
    public void failWhenCallingNonExistingProcedures() throws Throwable
    {
        try
        {
            // When
            dbmsOperations().procedureCallDbms( procedureName( "dbms", "iDoNotExist" ), new Object[0],
                    AnonymousContext.none() );
            fail( "This should never get here" );
        }
        catch ( Exception e )
        {
            // Then
            assertThat( e.getClass(), equalTo( ProcedureException.class ) );
        }
    }

    @Test
    public void listAllComponents() throws Throwable
    {
        // Given a running database

        // When
        RawIterator<Object[],ProcedureException> stream = procedureCallOpsInNewTx()
                .procedureCallRead( procedureName( "dbms", "components" ), new Object[0] );

        // Then
        assertThat( asList( stream ), contains( equalTo( new Object[]{"Neo4j Kernel",
                singletonList( Version.getNeo4jVersion() ), "community"} ) ) );

        commit();
    }

    @Test
    public void listAllIndexes() throws Throwable
    {
        // Given
        Statement statement = statementInNewTransaction( SecurityContext.AUTH_DISABLED );
        int labelId1 = statement.tokenWriteOperations().labelGetOrCreateForName( "Person" );
        int labelId2 = statement.tokenWriteOperations().labelGetOrCreateForName( "Age" );
        int propertyKeyId = statement.tokenWriteOperations().propertyKeyGetOrCreateForName( "foo" );
        //TODO: Add test support for composite indexes
        statement.schemaWriteOperations().indexCreate( SchemaDescriptorFactory.forLabel( labelId1, propertyKeyId ) );
        statement.schemaWriteOperations().uniquePropertyConstraintCreate( forLabel( labelId2, propertyKeyId ) );
        commit();

        //let indexes come online
        try ( Transaction tx = db.beginTx() )
        {
            db.schema().awaitIndexOnline( db.schema().getIndexes().iterator().next(), 20, SECONDS );
            tx.success();
        }

        // When
        RawIterator<Object[],ProcedureException> stream =
                procedureCallOpsInNewTx().procedureCallRead( procedureName( "db", "indexes" ), new Object[0] );

        Set<Object[]> result = new HashSet<>();
        while ( stream.hasNext() )
        {
            result.add( stream.next() );
        }

        // Then
        assertThat( result, containsInAnyOrder(
                new Object[]{"INDEX ON :Age(foo)", "ONLINE", "node_unique_property"},
                new Object[]{"INDEX ON :Person(foo)", "ONLINE", "node_label_property"}
        ) );
        commit();
    }
}
