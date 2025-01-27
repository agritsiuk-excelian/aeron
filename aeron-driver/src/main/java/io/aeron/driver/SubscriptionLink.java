/*
 * Copyright 2014-2019 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.driver;

import io.aeron.CommonContext;
import io.aeron.driver.media.ReceiveChannelEndpoint;
import io.aeron.driver.media.UdpChannel;
import org.agrona.concurrent.status.ReadablePosition;

import java.util.IdentityHashMap;

/**
 * Subscription registration from a client used for liveness tracking
 */
public abstract class SubscriptionLink implements DriverManagedResource
{
    protected final long registrationId;
    protected final int streamId;
    protected final int sessionId;
    protected final boolean hasSessionId;
    protected final boolean isSparse;
    protected final boolean isTether;
    protected boolean reachedEndOfLife = false;
    protected final CommonContext.InferableBoolean group;
    protected final String channel;
    protected final AeronClient aeronClient;
    protected final IdentityHashMap<Subscribable, ReadablePosition> positionBySubscribableMap;

    protected SubscriptionLink(
        final long registrationId,
        final int streamId,
        final String channel,
        final AeronClient aeronClient,
        final SubscriptionParams params)
    {
        this.registrationId = registrationId;
        this.streamId = streamId;
        this.channel = channel;
        this.aeronClient = aeronClient;
        this.hasSessionId = params.hasSessionId;
        this.sessionId = params.sessionId;
        this.isSparse = params.isSparse;
        this.isTether = params.isTether;
        this.group = params.group;

        positionBySubscribableMap = new IdentityHashMap<>(hasSessionId ? 1 : 8);
    }

    public long registrationId()
    {
        return registrationId;
    }

    public int streamId()
    {
        return streamId;
    }

    public int sessionId()
    {
        return sessionId;
    }

    public String channel()
    {
        return channel;
    }

    public ReceiveChannelEndpoint channelEndpoint()
    {
        return null;
    }

    public boolean isReliable()
    {
        return true;
    }

    public boolean isTether()
    {
        return isTether;
    }

    public boolean isSparse()
    {
        return isSparse;
    }

    public CommonContext.InferableBoolean group()
    {
        return group;
    }

    public boolean hasSessionId()
    {
        return hasSessionId;
    }

    public boolean matches(final NetworkPublication publication)
    {
        return false;
    }

    public boolean matches(final PublicationImage image)
    {
        return false;
    }

    public boolean matches(final IpcPublication publication)
    {
        return false;
    }

    public boolean matches(
        final ReceiveChannelEndpoint channelEndpoint, final int streamId, final SubscriptionParams params)
    {
        return false;
    }

    public boolean matches(final ReceiveChannelEndpoint channelEndpoint, final int streamId, final int sessionId)
    {
        return false;
    }

    public boolean isLinked(final Subscribable subscribable)
    {
        return positionBySubscribableMap.containsKey(subscribable);
    }

    public void link(final Subscribable subscribable, final ReadablePosition position)
    {
        positionBySubscribableMap.put(subscribable, position);
    }

    public void unlink(final Subscribable subscribable)
    {
        positionBySubscribableMap.remove(subscribable);
    }

    public void close()
    {
        positionBySubscribableMap.forEach((subscribable, position) -> subscribable.removeSubscriber(this, position));
    }

    public void onTimeEvent(final long timeNs, final long timeMs, final DriverConductor conductor)
    {
        if (aeronClient.hasTimedOut())
        {
            reachedEndOfLife = true;
            conductor.cleanupSubscriptionLink(this);
        }
    }

    public boolean hasReachedEndOfLife()
    {
        return reachedEndOfLife;
    }

    public boolean isWildcardOrSessionIdMatch(final int sessionId)
    {
        return !hasSessionId || this.sessionId == sessionId;
    }
}

class NetworkSubscriptionLink extends SubscriptionLink
{
    private final boolean isReliable;
    private final ReceiveChannelEndpoint channelEndpoint;

    NetworkSubscriptionLink(
        final long registrationId,
        final ReceiveChannelEndpoint channelEndpoint,
        final int streamId,
        final String channelUri,
        final AeronClient aeronClient,
        final SubscriptionParams params)
    {
        super(registrationId, streamId, channelUri, aeronClient, params);

        this.isReliable = params.isReliable;
        this.channelEndpoint = channelEndpoint;
    }

    public boolean isReliable()
    {
        return isReliable;
    }

    public ReceiveChannelEndpoint channelEndpoint()
    {
        return channelEndpoint;
    }

    public boolean matches(final PublicationImage image)
    {
        return image.channelEndpoint() == this.channelEndpoint &&
            image.streamId() == this.streamId &&
            isWildcardOrSessionIdMatch(image.sessionId());
    }

    public boolean matches(
        final ReceiveChannelEndpoint channelEndpoint, final int streamId, final SubscriptionParams params)
    {
        final boolean isExactWildcardOrSessionIdMatch =
            hasSessionId == params.hasSessionId && (!hasSessionId || this.sessionId == params.sessionId);

        return channelEndpoint == this.channelEndpoint &&
            streamId == this.streamId &&
            isExactWildcardOrSessionIdMatch;
    }

    public boolean matches(final ReceiveChannelEndpoint channelEndpoint, final int streamId, final int sessionId)
    {
        return channelEndpoint == this.channelEndpoint &&
            streamId == this.streamId &&
            isWildcardOrSessionIdMatch(sessionId);
    }
}

class IpcSubscriptionLink extends SubscriptionLink
{
    IpcSubscriptionLink(
        final long registrationId,
        final int streamId,
        final String channelUri,
        final AeronClient aeronClient,
        final SubscriptionParams params)
    {
        super(registrationId, streamId, channelUri, aeronClient, params);
    }

    public boolean matches(final IpcPublication publication)
    {
        return publication.streamId() == streamId && isWildcardOrSessionIdMatch(publication.sessionId());
    }
}

class SpySubscriptionLink extends SubscriptionLink
{
    private final UdpChannel udpChannel;

    SpySubscriptionLink(
        final long registrationId,
        final UdpChannel spiedChannel,
        final int streamId,
        final AeronClient aeronClient,
        final SubscriptionParams params)
    {
        super(registrationId, streamId, spiedChannel.originalUriString(), aeronClient, params);

        this.udpChannel = spiedChannel;
    }

    public boolean matches(final NetworkPublication publication)
    {
        return streamId == publication.streamId() &&
            isWildcardOrSessionIdMatch(publication.sessionId()) &&
            udpChannel.canonicalForm().equals(publication.channelEndpoint().udpChannel().canonicalForm());
    }
}

class UntetheredSubscription
{
    static final int ACTIVE = 0;
    static final int LINGER = 1;
    static final int RESTING = 2;

    int state = ACTIVE;
    long timeOfLastUpdateNs;
    final SubscriptionLink subscriptionLink;
    final ReadablePosition position;

    UntetheredSubscription(final SubscriptionLink subscriptionLink, final ReadablePosition position, final long timeNs)
    {
        this.subscriptionLink = subscriptionLink;
        this.position = position;
        this.timeOfLastUpdateNs = timeNs;
    }
}