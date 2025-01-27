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
package io.aeron.driver.exceptions;

import io.aeron.ErrorCode;

public class InvalidChannelException extends ControlProtocolException
{
    public InvalidChannelException(final ErrorCode code, final String msg)
    {
        super(code, msg);
    }

    public InvalidChannelException(final ErrorCode code, final Exception rootCause)
    {
        super(code, rootCause);
    }
}
