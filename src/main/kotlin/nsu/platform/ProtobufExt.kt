package nsu.platform

import io.grpc.Context
import nsu.nai.interceptor.AuthInterceptor

val Context.userId: Long get() = AuthInterceptor.USER_CONTEXT_KEY.get()