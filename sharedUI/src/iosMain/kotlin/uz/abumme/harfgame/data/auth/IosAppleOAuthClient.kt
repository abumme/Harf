package uz.abumme.harfgame.data.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import uz.abumme.harfgame.data.keyWindow
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAppleOAuthClient(
    private val googleClientId: String = "",
) : OAuthClient {

    override val isGoogleSupported: Boolean get() = googleClientId.isNotBlank()
    override val isAppleSupported: Boolean get() = true

    override suspend fun signInWithGoogle(): OAuthResult = OAuthResult.NotConfigured

    override suspend fun signInWithApple(): OAuthResult = suspendCancellableCoroutine { continuation ->
        val rawNonce = NSUUID.UUID().UUIDString + "-" + NSUUID.UUID().UUIDString
        val hashedNonce = sha256Hex(rawNonce)

        val appleIDProvider = ASAuthorizationAppleIDProvider()
        val request = appleIDProvider.createRequest().apply {
            requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
            nonce = hashedNonce
        }

        val controller = ASAuthorizationController(listOf(request))

        val delegate = object : NSObject(),
            ASAuthorizationControllerDelegateProtocol,
            ASAuthorizationControllerPresentationContextProvidingProtocol {

            override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor {
                return keyWindow() ?: UIWindow()
            }

            override fun authorizationController(
                controller: ASAuthorizationController,
                didCompleteWithAuthorization: ASAuthorization,
            ) {
                val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
                if (credential == null) {
                    if (continuation.isActive) continuation.resume(OAuthResult.Failed("Invalid credential"))
                    return
                }

                val tokenData = credential.identityToken
                val idToken = tokenData?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }

                if (idToken == null) {
                    if (continuation.isActive) continuation.resume(OAuthResult.Failed("Missing identity token"))
                    return
                }

                val fullName = credential.fullName
                val given = fullName?.givenName
                val family = fullName?.familyName
                val suggestedName = listOfNotNull(given, family).joinToString(" ").trim().takeIf { it.isNotEmpty() }

                if (continuation.isActive) {
                    continuation.resume(
                        OAuthResult.Token(
                            idToken = idToken,
                            nonce = rawNonce,
                            suggestedName = suggestedName,
                        )
                    )
                }
            }

            override fun authorizationController(
                controller: ASAuthorizationController,
                didCompleteWithError: NSError,
            ) {
                if (!continuation.isActive) return
                if (didCompleteWithError.code == ASAuthorizationErrorCanceled) {
                    continuation.resume(OAuthResult.Cancelled)
                } else {
                    continuation.resume(OAuthResult.Failed(didCompleteWithError.localizedDescription))
                }
            }
        }

        controller.delegate = delegate
        controller.presentationContextProvider = delegate

        // ASAuthorizationController holds delegate/presentationContextProvider weakly, so keep a
        // strong reference alive for the whole coroutine by capturing `delegate` in the handler.
        continuation.invokeOnCancellation {
            delegate.description
            controller.delegate = null
            controller.presentationContextProvider = null
        }

        controller.performRequests()
    }

    private fun sha256Hex(input: String): String {
        val bytes = input.encodeToByteArray()
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        bytes.usePinned { bytesPinned ->
            digest.usePinned { digestPinned ->
                CC_SHA256(
                    bytesPinned.addressOf(0),
                    bytes.size.toUInt(),
                    digestPinned.addressOf(0).reinterpret(),
                )
            }
        }
        return digest.joinToString("") { it.toInt().toString(16).padStart(2, '0') }
    }
}
