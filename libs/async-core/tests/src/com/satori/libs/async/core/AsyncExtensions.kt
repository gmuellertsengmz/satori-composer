package com.satori.libs.async.core

import kotlin.coroutines.experimental.*

suspend fun <T> com.satori.libs.async.api.IAsyncFuture<T>.await(): T {
  if (isCompleted) {
    return result.get()
  }
  return suspendCoroutine { cont: Continuation<T> ->
    onCompleted { ar ->
      if (ar.isSucceeded) {
        cont.resume(ar.value)
      } else {
        cont.resumeWithException(ar.error)
      }
    }
  }
}

fun <T> future(block: suspend () -> T): com.satori.libs.async.api.IAsyncFuture<T> {
  val future = object : AsyncFuture<T>(), Continuation<T> {
    override val context: CoroutineContext
      get() = EmptyCoroutineContext
    
    override fun resume(value: T) {
      succeed(value)
    }
    
    override fun resumeWithException(exception: Throwable) {
      fail(exception)
    }
  }
  block.startCoroutine(future)
  return future;
}

fun <R, T> future(scope: R, block: suspend R.() -> T): com.satori.libs.async.api.IAsyncFuture<T> {
  val future = object : AsyncFuture<T>(), Continuation<T> {
    override val context: CoroutineContext
      get() = EmptyCoroutineContext
    
    override fun resume(value: T) {
      succeed(value)
    }
    
    override fun resumeWithException(exception: Throwable) {
      fail(exception)
    }
  }
  block.startCoroutine(scope, future)
  return future;
}

fun<T, R> com.satori.libs.async.api.IAsyncFuture<T>.map(block: (T)->R ): com.satori.libs.async.api.IAsyncFuture<R> {
  val future = AsyncFuture<R>()
  onCompleted{ ar ->
    if(!ar.isSucceeded){
      future.fail(ar.error)
      return@onCompleted
    }
    val mappedResult: R
    try{
      mappedResult = block(ar.value)
    } catch (ex: Throwable){
      future.fail(ex)
      return@onCompleted
    }
    future.succeed(mappedResult)
  }
  return future
}

val<T> com.satori.libs.async.api.IAsyncFuture<T>.value:T?
  get() = result?.value


