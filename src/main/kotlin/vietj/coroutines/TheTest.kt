package vietj.coroutines

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.streams.ReadStream

class TheTest {

    fun testAsyncFuture(doneHandler: Handler<String>) {
        sync {
            val vertx = Vertx.vertx()
            val fut = Future.future<String>()
            vertx.setTimer(50) {
                fut.complete("the_value");
            }
            val result = await(fut)
            doneHandler.handle(result)
        }
    }

    fun testAsyncResultHandler(doneHandler: Handler<String>) {
        val vertx = Vertx.vertx();
        vertx.eventBus().consumer<String>("the-address", { it.reply("pong") })
        sync {
            val msg = await<Message<String>> {
                vertx.eventBus().send("the-address", "ping", it)
            }
            doneHandler.handle(msg.body())
        }
    }

    fun readStream(stream: ReadStream<String>, itemHandler: Handler<String>, doneHandler: Handler<Void>) {
        sync {
            val seq = ReadStreamSequence(stream)
            while (true) {
                val next = next(seq)
                if (next.isPresent) {
                    itemHandler.handle(next.get())
                } else {
                    break
                }
            }
            doneHandler.handle(null)
        }
    }

    fun readStreamWithBackPressure(stream: ReadStream<String>, resume: Future<Void>, itemHandler: Handler<String>, doneHandler: Handler<Void>) {
        sync {
            val seq = ReadStreamSequence(stream)
            itemHandler.handle(next(seq).get())
            itemHandler.handle(next(seq).get())
            itemHandler.handle(next(seq).get())
            await(resume);
            while (true) {
                val next = next(seq)
                if (next.isPresent) {
                    itemHandler.handle(next.get())
                } else {
                    break;
                }
            }
            doneHandler.handle(null)
        }
    }
}