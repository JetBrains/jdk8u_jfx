/*
 * Copyright (C) 2013 Apple Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE INC. AND ITS CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL APPLE INC. OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

#import "config.h"
#import "PublicSuffix.h"

#import "WebCoreSystemInterface.h"
#import "WebCoreNSURLExtras.h"

#if ENABLE(PUBLIC_SUFFIX_LIST)

@interface NSString (WebCoreNSURLExtras)
- (BOOL)_web_looksLikeIPAddress;
@end

namespace WebCore {

bool isPublicSuffix(const String& domain)
{
    NSString *host = decodeHostName(domain);
    return host && wkIsPublicSuffix(host);
}

String topPrivatelyControlledDomain(const String& domain)
{
    if ([domain _web_looksLikeIPAddress])
        return domain;

    size_t separatorPosition;
    for (unsigned labelStart = 0; (separatorPosition = domain.find('.', labelStart)) != notFound; labelStart = separatorPosition + 1) {
        if (isPublicSuffix(domain.substring(separatorPosition + 1)))
            return domain.substring(labelStart);
    }
    return String();
}

}

#endif
