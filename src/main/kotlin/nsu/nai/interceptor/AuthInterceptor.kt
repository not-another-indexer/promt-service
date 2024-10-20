package nsu.nai.interceptor

import io.grpc.*
import nsu.nai.usecase.auth.JwtTokenFactory
import io.github.oshai.kotlinlogging.KotlinLogging

class AuthInterceptor : ServerInterceptor {

    private val logger = KotlinLogging.logger {}

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val tokenKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
        val token = headers.get(tokenKey)

        return if (token != null && token.startsWith("Bearer ")) {
            val jwt = token.substring("Bearer ".length)
            val userId = JwtTokenFactory.getUserIdFromToken(jwt)

            if (userId != null) {
                val contextWithUser = Context.current().withValue(USER_CONTEXT_KEY, userId)
                Contexts.interceptCall(contextWithUser, call, headers, next)
            } else {
                logger.warn { "Invalid JWT token: No user ID found." }
                call.close(Status.UNAUTHENTICATED.withDescription("Invalid JWT token"), headers)
                object : ServerCall.Listener<ReqT>() {}
            }
        } else {
            logger.warn { "Authorization header is missing or invalid." }
            call.close(Status.UNAUTHENTICATED.withDescription("Authorization header is missing or invalid"), headers)
            object : ServerCall.Listener<ReqT>() {}
        }
    }

    companion object {
        val USER_CONTEXT_KEY: Context.Key<Long> = Context.key("user")
    }
}