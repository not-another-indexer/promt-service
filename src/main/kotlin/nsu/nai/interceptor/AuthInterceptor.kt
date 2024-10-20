package nsu.nai.interceptor

import io.grpc.*
import nsu.nai.usecase.auth.JwtTokenFactory

class AuthInterceptor : ServerInterceptor {

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        try {
            val token = headers.get(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER))

            if (token != null && token.startsWith("Bearer ")) {

                val user = JwtTokenFactory.getUserIdFromToken(token.substring("Bearer ".length))

                val contextWithUser = Context.current().withValue(USER_CONTEXT_KEY, user)
                return Contexts.interceptCall(contextWithUser, call, headers, next)
            } else {
                call.close(
                    Status.UNAUTHENTICATED.withDescription("Authorization header is missing or invalid"),
                    headers
                )
                return object : ServerCall.Listener<ReqT>() {}
            }
        } catch (e: Exception) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid JWT token"), headers)
            return object : ServerCall.Listener<ReqT>() {}
        }
    }

    companion object {
        val USER_CONTEXT_KEY: Context.Key<Long> = Context.key("user")
    }
}
