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
package io.aeron;

import io.aeron.driver.MediaDriver;
import io.aeron.exceptions.AeronException;
import org.agrona.CloseHelper;
import org.agrona.ErrorHandler;
import org.agrona.collections.MutableReference;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class ReentrantClientTest
{
    final MediaDriver mediaDriver = MediaDriver.launch(new MediaDriver.Context().dirDeleteOnStart(true));

    @After
    public void after()
    {
        CloseHelper.close(mediaDriver);
        mediaDriver.context().deleteAeronDirectory();
    }

    @Test
    public void shouldThrowWhenReentering()
    {
        final MutableReference<Throwable> expectedException = new MutableReference<>();
        final ErrorHandler errorHandler = expectedException::set;

        try (Aeron aeron = Aeron.connect(new Aeron.Context().errorHandler(errorHandler)))
        {
            final String channel = CommonContext.IPC_CHANNEL;
            final AvailableImageHandler mockHandler = mock(AvailableImageHandler.class);
            doAnswer((invocation) -> aeron.addSubscription(channel, 3))
                .when(mockHandler).onAvailableImage(any(Image.class));

            final Subscription sub = aeron.addSubscription(channel, 1, mockHandler, null);
            final Publication pub = aeron.addPublication(channel, 1);

            verify(mockHandler, timeout(5000)).onAvailableImage(any(Image.class));

            pub.close();
            sub.close();

            assertThat(expectedException.get(), instanceOf(AeronException.class));
        }
    }
}
