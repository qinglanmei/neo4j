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

package org.neo4j.bolt.logging;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.codehaus.jackson.map.ObjectMapper;

import org.neo4j.kernel.api.exceptions.Status;

import static java.lang.String.format;

/**
 * Logs Bolt messages for a client-server pair.
 */
class BoltMessageLoggerImpl implements BoltMessageLogger
{
    private static final ObjectMapper jsonObjectMapper = new ObjectMapper();
    private static final Supplier<String> PLACEHOLDER_DETAIL_SUPPLIER = () -> "-";
    private final BoltMessageLog messageLog;

    private final String remoteAddress;
    private Channel channel;

    private static final String BOLT_X_CORRELATION_ID_HEADER = "Bolt-X-CorrelationId";
    public static final AttributeKey<String> CORRELATION_ATTRIBUTE_KEY = AttributeKey.valueOf(
            BOLT_X_CORRELATION_ID_HEADER );

    BoltMessageLoggerImpl( BoltMessageLog messageLog, Channel channel )
    {
        this.messageLog = messageLog;
        this.channel = channel;
        this.remoteAddress = remoteAddress( channel );
    }

    @Override
    public void clientEvent( String eventName )
    {
        clientEvent( eventName, PLACEHOLDER_DETAIL_SUPPLIER );
    }

    @Override
    public void clientEvent( String eventName, Supplier<String> detailsSupplier )
    {
        infoLogger().accept( format( "C: %s", eventName ), detailsSupplier.get() );
    }

    @Override
    public void clientError( String eventName, String errorMessage, Supplier<String> detailsSupplier )
    {
        errorLoggerWithArgs( errorMessage ).accept( format( "C: <%s>", eventName ), detailsSupplier.get() );
    }

    @Override
    public void serverEvent( String eventName )
    {
        serverEvent( eventName, PLACEHOLDER_DETAIL_SUPPLIER );
    }

    @Override
    public void serverEvent( String eventName, Supplier<String> detailsSupplier )
    {
        infoLogger().accept( format( "S: %s", eventName ), detailsSupplier.get() );
    }

    @Override
    public void serverError( String eventName, String errorMessage )
    {
        errorLogger( errorMessage ).accept( format( "S: <%s>", eventName ) );
    }

    @Override
    public void serverError( String eventName, Status status )
    {
        serverError( eventName, status.code().serialize() );
    }

    @Override
    public void logInit( String userAgent, Map<String,Object> authToken )
    {
        // log only auth toke keys, not values that include password
        clientEvent( "INIT", () -> format( "%s %s", userAgent, json( authToken.keySet() ) ) );
    }

    @Override
    public void logRun( String statement, Supplier<Map<String, Object>> parametersSupplier )
    {
        clientEvent( "RUN", () -> format( "%s %s", statement, json( parametersSupplier.get() ) ) );
    }

    @Override
    public void logPullAll()
    {
        clientEvent( "PULL_ALL", PLACEHOLDER_DETAIL_SUPPLIER );
    }

    @Override
    public void logDiscardAll()
    {
        clientEvent( "DISCARD_ALL", PLACEHOLDER_DETAIL_SUPPLIER );
    }

    @Override
    public void logAckFailure()
    {
        clientEvent( "ACK_FAILURE", PLACEHOLDER_DETAIL_SUPPLIER );
    }

    @Override
    public void logReset()
    {
        clientEvent( "RESET", PLACEHOLDER_DETAIL_SUPPLIER );
    }

    @Override
    public void logSuccess( Supplier<String> metadataSupplier )
    {
        serverEvent( "SUCCESS", () -> json( metadataSupplier.get() ) );
    }

    @Override
    public void logFailure( Status status )
    {
        serverEvent( "FAILURE", () -> status.code().serialize() );
    }

    @Override
    public void logIgnored()
    {
        serverEvent( "IGNORED", PLACEHOLDER_DETAIL_SUPPLIER );
    }

    private Consumer<String> errorLogger( String errorMessage )
    {
        if ( !channel.hasAttr( CORRELATION_ATTRIBUTE_KEY ) )
        {
            channel.attr( CORRELATION_ATTRIBUTE_KEY ).set( randomCorrelationIdGenerator() );
        }

        String boltCorrelationId = channel.attr( CORRELATION_ATTRIBUTE_KEY ).get();
        return formatMessageWithEventName ->
                messageLog.error( remoteAddress, boltCorrelationId, formatMessageWithEventName, errorMessage );
    }

    private String randomCorrelationIdGenerator()
    {
        return UUID.randomUUID().toString();
    }

    private BiConsumer<String, String> infoLogger()
    {
        if ( !channel.hasAttr( CORRELATION_ATTRIBUTE_KEY ) )
        {
            channel.attr( CORRELATION_ATTRIBUTE_KEY ).set( randomCorrelationIdGenerator() );
        }

        String boltCorrelationId = channel.attr( CORRELATION_ATTRIBUTE_KEY ).get();
        return ( formatMessageWithEventName, details ) ->
                messageLog.info( remoteAddress,
                        boltCorrelationId,
                        format( "%s %s", formatMessageWithEventName, details ) );
    }

    private BiConsumer<String, String> errorLoggerWithArgs( String errorMessage )
    {
        return ( formatMessageWithEventName, details ) ->
                errorLogger( errorMessage ).accept( format( "%s %s", formatMessageWithEventName, details ) );
    }

    private static String remoteAddress( Channel channel )
    {
        return channel.remoteAddress().toString();
    }

    private static String json( Object arg )
    {
        try
        {
            return jsonObjectMapper.writeValueAsString( arg );
        }
        catch ( IOException e )
        {
            return "?";
        }
    }

}
