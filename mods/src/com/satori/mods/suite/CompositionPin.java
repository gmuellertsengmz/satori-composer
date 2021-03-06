package com.satori.mods.suite;

import com.satori.libs.async.api.*;
import com.satori.libs.async.core.*;
import com.satori.mods.api.*;

import java.util.*;

import com.fasterxml.jackson.databind.*;

public class CompositionPin implements IModOutput {
  public final ArrayList<IModInput> connectors;
  
  public CompositionPin() {
    this(new ArrayList<>());
  }
  
  public CompositionPin(ArrayList<IModInput> connectors) {
    this.connectors = connectors;
  }
  
  @Override
  public void yield(JsonNode data, IAsyncHandler cont) throws Exception {
    if (connectors.size() == 0) {
      cont.succeed();
      return;
    }
    if (connectors.size() == 1) {
      connectors.get(0).process(data, cont);
      return;
    }
    doYieldLoop(connectors.iterator(), data, cont);
  }
  
  @Override
  public IAsyncFuture yield(JsonNode data) throws Exception {
    if (connectors.size() == 0) {
      return AsyncResults.succeededNull;
    }
    if (connectors.size() == 1) {
      return connectors.get(0).process(data);
    }
    return doYieldLoop(connectors.iterator(), data);
  }
  

  /*private void doYieldLoop(Iterator<IModInput> itor, JsonNode msg, IAsyncHandler cont) throws Exception {
    IModInput h = itor.next();
    if (!itor.hasNext()) {
      h.process(msg, cont);
    }
    h.process(msg, ar -> {
      if (!ar.isSucceeded()) {
        cont.fail(ar.getError());
        return;
      }
      try {
        doYieldLoop(itor, msg, cont);
      } catch (Throwable e) {
        cont.fail(e);
      }
    });
  }*/
  
  public void doYieldLoop(Iterator<IModInput> itor, JsonNode msg, IAsyncHandler cont) throws Exception {
    while (true) {
      
      IModInput h = itor.next();
      if (!itor.hasNext()) {
        // this was the last element
        h.process(msg, cont);
        return;
      }
      
      IAsyncFuture<?> future = h.process(msg);
      if (!future.isCompleted()) {
        // operation still in progress, set continuation and exit
        future.onCompleted(ar -> {
          if (!ar.isSucceeded()) {
            cont.fail(ar.getError());
            return;
          }
          try {
            doYieldLoop(itor, msg, cont);
          } catch (Exception e) {
            cont.fail(e);
          }
        });
        return;
      }
      
      // operation was completed immediately
      IAsyncResult<?> ar = future.getResult();
      if (!ar.isSucceeded()) {
        // abort loop with failure
        cont.fail(ar.getError());
        return;
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  public IAsyncFuture doYieldLoop(Iterator<IModInput> itor, JsonNode msg) throws Exception {
    AsyncForkJoin forkJoin = new AsyncForkJoin();
    while (itor.hasNext()) {
      IModInput input = itor.next();
      try {
        IAsyncFuture future = input.process(msg);
        if (!future.isCompleted()) {
          forkJoin.inc();
          future.onCompleted(forkJoin);
        }
      } catch (Throwable e) {
        forkJoin.dec();
      }
    }
    forkJoin.succeed();
    return forkJoin;
  }
  
  public void linkOutput(IModInput output) {
    connectors.add(output);
  }
  
  public void linkOutput(IMod mod, String inputName) {
    linkOutput(new IModInput() {
      @Override
      public void process(JsonNode data, IAsyncHandler cont) throws Exception {
        mod.onInput(inputName, data, cont);
      }
      
    });
  }
  
  public void linkOutput(IMod mod) {
    linkOutput(mod, "default");
  }
}
