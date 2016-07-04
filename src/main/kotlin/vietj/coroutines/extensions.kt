package vietj.coroutines

import io.vertx.core.http.HttpServer;
import io.vertx.core.Future;
import io.vertx.core.Handler
import io.vertx.core.streams.ReadStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

fun HttpServer.listen2(port: Int, host: String): Future<HttpServer> {
    val f = Future.future<HttpServer>()
    this.listen(port, host, f.completer());
    return f;
}

fun sync(coroutine c: VertxController.() -> Continuation<Unit>): Unit {
    val controller = VertxController()
    val cont = c.invoke(controller);
    cont.resume(Unit)
}

class ReadStreamSequence<T>(val stream: ReadStream<T>) {

    val buffer = LinkedList<T>()
    val done = AtomicBoolean()
    val dataHandler = AtomicReference<Handler<T>>()
    val endHandler = AtomicReference<Handler<Void>>()

    init {
        stream.handler {
            val handler = dataHandler.get()
            if (handler != null) {
                dataHandler.set(null)
                endHandler.set(null)
                handler.handle(it)
            } else {
                buffer.add(it)
                if (buffer.size > 10) {
                    stream.pause()
                }
            }
        }
        stream.endHandler {
            done.set(true)
            endHandler.get()?.handle(null)
            dataHandler.set(null)
            endHandler.set(null)
        }
    }

    fun consume(handler: (Optional<T>) -> Unit) {
        if (buffer.size > 0) {
            val first = buffer.removeFirst();
            if (buffer.size == 0) {
                stream.resume()
            }
            handler(Optional.of(first))
        } else if (done.get()) {
            handler(Optional.empty())
        } else {
            dataHandler.set(Handler {
                handler(Optional.of(it))
            })
            endHandler.set(Handler {
                handler(Optional.empty())
            })
        }
    }
}

class VertxController {

    suspend fun <T> next(stream: ReadStreamSequence<T>, c: Continuation<Optional<T>>) {
        stream.consume { c.resume(it) }
    }

    suspend fun <T> await(fut: Future<T>, c: Continuation<T>) {
        fut.setHandler({
            if (fut.succeeded()) {
                c.resume(fut.result());
            } else {
                c.resumeWithException(fut.cause())
            }
        })
    }

    suspend fun <T> await(f: (Future<T>) -> Unit, c: Continuation<T>) {
        val fut = Future.future<T>()
        f(fut);
        await(fut, c);
    }
}
