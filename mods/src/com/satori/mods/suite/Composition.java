package com.satori.mods.suite;

import com.satori.libs.async.api.*;
import com.satori.mods.api.*;
import com.satori.mods.core.*;
import com.satori.mods.core.config.*;
import com.satori.mods.core.stats.*;

import java.util.*;

import com.fasterxml.jackson.databind.*;
import org.slf4j.*;

public class Composition extends Mod {
  public static final Logger log = LoggerFactory.getLogger(Composition.class);
  
  private final ArrayList<CompositionNode> mods = new ArrayList<>();
  private final HashMap<String, CompositionPin> pins = new HashMap<>();
  
  public Composition() throws Exception {
  }
  
  public Composition(JsonNode config) throws Exception {
    this(config, DefaultModResolver.instance);
  }
  
  public Composition(JsonNode config, IModResolver modResolver) throws Exception {
    this(Config.parseAndValidate(config, CompositionSettings.class), modResolver);
  }
  
  public Composition(CompositionSettings config) throws Exception {
    this(config, DefaultModResolver.instance);
  }

  public Composition(CompositionSettings config, IModResolver modResolver) throws Exception {
    this(config.mods, modResolver);

    config.outputs.forEach(this::linkOutput);
  }
  
  public Composition(HashMap<String, CompositionNodeConfig> mods) throws Exception {
    this(mods, DefaultModResolver.instance);
  }
  
  public Composition(HashMap<String, CompositionNodeConfig> mods, IModResolver modResolver) throws Exception {
    mods.forEach((modName, modConf) -> {
      final IMod mod;
      try {
        mod = modResolver.create(modConf.type, modConf.settings);
      } catch (Exception e){
        log.error("failed to create mod '{}:{}'", modName, modConf.type);
        throw new RuntimeException(e);
      } catch (Throwable e){
        log.error("failed to create mod '{}:{}'", modName, modConf.type);
        throw e;
      }
      addMod(modName, mod, modConf.connectors);
    });
  }
  
  // IMod implementation
  
  @Override
  public void init(IModContext context) throws Exception {
    super.init(context);
    for (IMod mod : mods) {
      mod.init(context);
    }
    log.info("initialized");
  }
  
  @Override
  public void onStart() throws Exception {
    for (IMod mod : mods) {
      mod.onStart();
    }
    log.info("started");
  }
  
  @Override
  public void onStop() throws Exception {
    for (IMod mod : mods) {
      mod.onStop();
    }
    log.info("stopped");
  }
  
  @Override
  public void dispose() throws Exception {
    super.dispose();
    for (IMod mod : mods) {
      mod.dispose();
    }
    log.info("disposed");
  }
  
  @Override
  public void onPulse() {
    log.debug("pulse received");
    mods.forEach(mod -> mod.onPulse());
  }
  
  @Override
  public void onStats(StatsCycle cycle, IStatsCollector collector) {
    log.debug("collecting statistic...");
    mods.forEach(mod -> mod.onStats(cycle, collector));
  }
  
  @Override
  public void onInput(String input, JsonNode data, IAsyncHandler cont) throws Exception {
    CompositionPin pin = pins.get(input);
    if (pin == null) {
      cont.fail(new Exception("connectors not found"));
      return;
    }
    pin.yield(data, cont);
  }
  
  @Override
  public IAsyncFuture onInput(String inputName, JsonNode data) throws Exception {
    CompositionPin pin = pins.get(inputName);
    if (pin == null) {
      return AsyncResults.failed("connectors not found");
    }
    return pin.yield(data);
  }
  
  public void linkOutput(String pinRef) {
    CompositionPin pin = pins.computeIfAbsent(pinRef, k -> new CompositionPin());
    pin.linkOutput(new IModInput() {
      @Override
      public void process(JsonNode data, IAsyncHandler cont) throws Exception {
        yield(data, cont);
      }
      @Override
      public IAsyncFuture process(JsonNode data) throws Exception {
        return yield(data);
      }
    });
  }
  
  public void linkModInput(IMod mod, String inputName, String pinRef) {
    CompositionPin pin = pins.computeIfAbsent(pinRef, k -> new CompositionPin());
    pin.linkOutput(new IModInput() {
      @Override
      public void process(JsonNode data, IAsyncHandler cont) throws Exception {
        mod.onInput(inputName, data, cont);
      }
      @Override
      public IAsyncFuture process(JsonNode data) throws Exception {
        return mod.onInput(inputName, data);
      }
    });
  }
  
  public CompositionNode addMod(String name, IMod mod, HashMap<String, ArrayList<String>> inputs) {
    CompositionPin pin = pins.computeIfAbsent(name, k -> new CompositionPin());
    CompositionNode node = new CompositionNode(name, mod, pin);
    this.mods.add(node);
    if (inputs != null) {
      inputs.forEach((inputName, pinRefs) -> {
        if (pinRefs == null) {
          return;
        }
        pinRefs.forEach(pinRef -> {
          linkModInput(mod, inputName, pinRef);
        });
      });
    }
    return node;
  }
  
  public CompositionNode addMod(String name, IMod mod) {
    return addMod(name, mod, null);
  }
}
