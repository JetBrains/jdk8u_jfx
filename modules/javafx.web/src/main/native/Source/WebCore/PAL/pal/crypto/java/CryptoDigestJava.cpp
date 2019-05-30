/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 */

#include "config.h"

#include "CryptoDigest.h"
#undef WEBCORE_EXPORT
#define WEBCORE_EXPORT
#include <jni.h>
#include <wtf/java/JavaEnv.h>
#include <wtf/java/JavaRef.h>

namespace PAL {

namespace CryptoDigestInternal {

inline jclass GetMessageDigestClass(JNIEnv* env)
{
    static JGClass messageDigestCls(
        env->FindClass("com/sun/webkit/security/WCMessageDigest"));
    ASSERT(messageDigestCls);
    return messageDigestCls;
}

inline JLObject GetMessageDigestInstance(jstring algorithm)
{
    JNIEnv* env = WebCore_GetJavaEnv();
    if (!env) {
        return { };
    }

    static jmethodID midGetInstance = env->GetStaticMethodID(
        GetMessageDigestClass(env),
        "getInstance",
        "(Ljava/lang/String;)Lcom/sun/webkit/security/WCMessageDigest;");
    ASSERT(midGetInstance);
    JLObject jDigest = env->CallStaticObjectMethod(GetMessageDigestClass(env), midGetInstance, algorithm);
    if (CheckAndClearException(env)) {
        return { };
    }
    return jDigest;
}

jstring toJavaMessageDigestAlgorithm(CryptoDigest::Algorithm algorithm)
{
    JNIEnv* env = WebCore_GetJavaEnv();

    const char* algorithmStr = "";
    switch (algorithm) {
        case CryptoDigest::Algorithm::SHA_1:
            algorithmStr = "SHA-1";
            break;
        case CryptoDigest::Algorithm::SHA_224:
            algorithmStr = "SHA-224";
            break;
        case CryptoDigest::Algorithm::SHA_256:
            algorithmStr = "SHA-256";
            break;
        case CryptoDigest::Algorithm::SHA_384:
            algorithmStr = "SHA-384";
            break;
        case CryptoDigest::Algorithm::SHA_512:
            algorithmStr = "SHA-512";
            break;
    }
    return env->NewStringUTF(algorithmStr);
}

} // namespace CryptoDigestInternal

struct CryptoDigestContext {
    JGObject jDigest { };
};

CryptoDigest::CryptoDigest()
    : m_context(new CryptoDigestContext)
{
}

CryptoDigest::~CryptoDigest()
{
}

std::unique_ptr<CryptoDigest> CryptoDigest::create(CryptoDigest::Algorithm algorithm)
{
    using namespace CryptoDigestInternal;
    auto digest = std::unique_ptr<CryptoDigest>(new CryptoDigest);
    digest->m_context->jDigest = GetMessageDigestInstance(toJavaMessageDigestAlgorithm(algorithm));
    return digest;
}

void CryptoDigest::addBytes(const void* input, size_t length)
{
    using namespace CryptoDigestInternal;

    JNIEnv* env = WebCore_GetJavaEnv();
    if (!m_context->jDigest || !env) {
        return;
    }

    static jmethodID midUpdate = env->GetMethodID(
        GetMessageDigestClass(env),
        "addBytes",
        "(Ljava/nio/ByteBuffer;)V");
    ASSERT(midUpdate);
    env->CallVoidMethod(jobject(m_context->jDigest), midUpdate, env->NewDirectByteBuffer(const_cast<void*>(input), length));
}

Vector<uint8_t> CryptoDigest::computeHash()
{
    using namespace CryptoDigestInternal;

    JNIEnv* env = WebCore_GetJavaEnv();
    if (!m_context->jDigest || !env) {
        return { };
    }

    static jmethodID midDigest = env->GetMethodID(
        GetMessageDigestClass(env),
        "computeHash",
        "()[B");
    ASSERT(midUpdate);

    JLocalRef<jbyteArray> jDigestBytes = static_cast<jbyteArray>(env->CallObjectMethod(jobject(m_context->jDigest), midDigest));
    void* digest = env->GetPrimitiveArrayCritical(static_cast<jbyteArray>(jDigestBytes), 0);
    if (!digest) {
        return { };
    }

    Vector<uint8_t> result;
    result.append(static_cast<uint8_t*>(digest), env->GetArrayLength(jDigestBytes));
    env->ReleasePrimitiveArrayCritical(jDigestBytes, digest, 0);
    return result;
}

} // namespace PAL
