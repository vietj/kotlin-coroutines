package vietj.test;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;
import org.junit.Test;
import vietj.coroutines.TheTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CoroutineTest {

  @Test
  public void testAsyncFuture() throws Exception {
    TheTest test = new TheTest();
    CompletableFuture<String> result = new CompletableFuture<>();
    test.testAsyncFuture(result::complete);
    assertEquals("the_value", result.get(10, TimeUnit.SECONDS));
  }

  @Test
  public void testAsyncHandler() throws Exception {
    TheTest test = new TheTest();
    CompletableFuture<String> result = new CompletableFuture<>();
    test.testAsyncHandler(result::complete);
    assertEquals("pong", result.get(10, TimeUnit.SECONDS));
  }

  @Test
  public void testReadStream() throws Exception {
    TheTest test = new TheTest();
    ReadStreamImpl<String> stream = new ReadStreamImpl<>();
    LinkedList<String> items = new LinkedList<>();
    AtomicInteger done = new AtomicInteger();
    test.readStream(stream, items::add, v -> done.incrementAndGet());
    assertNotNull(stream.dataHandler);
    assertNotNull(stream.endHandler);
    assertEquals(Collections.emptyList(), items);
    assertEquals(0, done.get());
    stream.dataHandler.handle("zero");
    assertEquals(Collections.singletonList("zero"), items);
    assertEquals(0, done.get());
    stream.dataHandler.handle("one");
    assertEquals(Arrays.asList("zero", "one"), items);
    assertEquals(0, done.get());
    stream.endHandler.handle(null);
    assertEquals(Arrays.asList("zero", "one"), items);
    assertEquals(1, done.get());
  }

  @Test
  public void testReadStreamWithBackPressure() throws Exception {
    TheTest test = new TheTest();
    ReadStreamImpl<String> stream = new ReadStreamImpl<>();
    LinkedList<String> items = new LinkedList<>();
    AtomicInteger done = new AtomicInteger();
    Future<Void> resume = Future.future();
    test.readStreamWithBackPressure(stream, resume, items::add, v -> done.incrementAndGet());
    assertNotNull(stream.dataHandler);
    assertNotNull(stream.endHandler);
    assertEquals(Collections.emptyList(), items);
    assertEquals(0, done.get());
    stream.dataHandler.handle("0");
    assertEquals(Collections.singletonList("0"), items);
    assertEquals(0, done.get());
    stream.dataHandler.handle("1");
    assertEquals(Arrays.asList("0", "1"), items);
    assertEquals(0, done.get());
    stream.dataHandler.handle("2");
    assertEquals(Arrays.asList("0", "1", "2"), items);
    assertEquals(0, done.get());
    for (int i = 0;i < 10;i++) {
      stream.dataHandler.handle("" + (3 + i));
      assertEquals(Arrays.asList("0", "1", "2"), items);
      assertEquals(0, done.get());
      assertFalse(stream.paused);
    }
    stream.dataHandler.handle("13");
    assertEquals(Arrays.asList("0", "1", "2"), items);
    assertEquals(0, done.get());
    assertTrue(stream.paused);
    resume.complete();
    assertEquals(Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"), items);
    assertEquals(0, done.get());
    assertFalse(stream.paused);
    stream.dataHandler.handle("14");
    assertEquals(Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14"), items);
    assertEquals(0, done.get());
    assertFalse(stream.paused);
    stream.endHandler.handle(null);
    assertEquals(Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14"), items);
    assertEquals(1, done.get());
    assertFalse(stream.paused);
  }

  class ReadStreamImpl<T> implements ReadStream<T> {

    private Handler<T> dataHandler;
    private Handler<Void> endHandler;
    private boolean paused;

    @Override
    public ReadStream<T> exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    @Override
    public ReadStream<T> handler(Handler<T> handler) {
      dataHandler = handler;
      return this;
    }

    @Override
    public ReadStream<T> pause() {
      paused = true;
      return this;
    }

    @Override
    public ReadStream<T> resume() {
      paused = false;
      return this;
    }

    @Override
    public ReadStream<T> endHandler(Handler<Void> handler) {
      endHandler = handler;
      return this;
    }
  }
}
