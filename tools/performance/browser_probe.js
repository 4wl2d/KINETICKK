// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

async (page) => {
  const probeConfig = await page.evaluate(() => {
    const encoded = window.location.hash.slice(1);
    if (!encoded) {
      throw new Error("Browser benchmark configuration is missing from the bootstrap URL");
    }
    const binary = atob(encoded.replace(/-/g, "+").replace(/_/g, "/"));
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  });

  page.setDefaultTimeout(probeConfig.timeoutMillis);
  page.setDefaultNavigationTimeout(probeConfig.timeoutMillis);

  let phase = "bootstrap";
  const consoleMessages = [];
  const pageErrors = [];
  const requestFailures = [];
  const httpErrors = [];

  const appendBounded = (collection, value, maximum) => {
    if (collection.length < maximum) {
      collection.push(value);
    }
  };

  page.on("console", (message) => {
    appendBounded(consoleMessages, {
      phase,
      type: message.type(),
      text: message.text().slice(0, 4_096),
      location: message.location(),
    }, 500);
  });
  page.on("pageerror", (error) => {
    appendBounded(pageErrors, {
      phase,
      name: error.name || "Error",
      message: String(error.message || error).slice(0, 8_192),
      stack: error.stack ? String(error.stack).slice(0, 16_384) : null,
    }, 100);
  });
  page.on("requestfailed", (request) => {
    appendBounded(requestFailures, {
      phase,
      method: request.method(),
      resourceType: request.resourceType(),
      url: request.url(),
      errorText: request.failure() ? request.failure().errorText : "unknown",
    }, 200);
  });
  page.on("response", (response) => {
    if (response.status() >= 400) {
      appendBounded(httpErrors, {
        phase,
        status: response.status(),
        statusText: response.statusText(),
        url: response.url(),
      }, 200);
    }
  });

  const runtimeSnapshot = async () => page.evaluate(() => {
    const finite = (value) => Number.isFinite(value) ? value : null;
    const navigation = performance.getEntriesByType("navigation").at(-1) || null;
    const paints = performance.getEntriesByType("paint").map((entry) => ({
      name: entry.name,
      startTimeMillis: finite(entry.startTime),
      durationMillis: finite(entry.duration),
    }));
    const resources = performance.getEntriesByType("resource").map((entry) => ({
      name: entry.name,
      initiatorType: entry.initiatorType || "other",
      startTimeMillis: finite(entry.startTime),
      durationMillis: finite(entry.duration),
      fetchStartMillis: finite(entry.fetchStart),
      responseStartMillis: finite(entry.responseStart),
      responseEndMillis: finite(entry.responseEnd),
      transferSizeBytes: finite(entry.transferSize),
      encodedBodySizeBytes: finite(entry.encodedBodySize),
      decodedBodySizeBytes: finite(entry.decodedBodySize),
      nextHopProtocol: entry.nextHopProtocol || null,
      renderBlockingStatus: entry.renderBlockingStatus || null,
    }));

    const sum = (values) => values.reduce((total, value) => total + (value || 0), 0);
    const aggregateResources = (entries) => ({
      count: entries.length,
      transferSizeBytes: sum(entries.map((entry) => entry.transferSizeBytes)),
      encodedBodySizeBytes: sum(entries.map((entry) => entry.encodedBodySizeBytes)),
      decodedBodySizeBytes: sum(entries.map((entry) => entry.decodedBodySizeBytes)),
      durationMillis: sum(entries.map((entry) => entry.durationMillis)),
      cacheEligibleZeroTransferCount: entries.filter(
        (entry) => entry.transferSizeBytes === 0 && (entry.decodedBodySizeBytes || 0) > 0,
      ).length,
    });

    const byInitiator = {};
    for (const entry of resources) {
      const key = entry.initiatorType || "other";
      if (!byInitiator[key]) {
        byInitiator[key] = [];
      }
      byInitiator[key].push(entry);
    }

    const hasSuffix = (entry, suffix) => {
      try {
        return new URL(entry.name).pathname.toLowerCase().endsWith(suffix);
      } catch (_error) {
        return entry.name.toLowerCase().split("?")[0].endsWith(suffix);
      }
    };
    const deepQueryAll = (selector) => {
      const matches = [];
      const visit = (root) => {
        matches.push(...root.querySelectorAll(selector));
        for (const element of root.querySelectorAll("*")) {
          if (element.shadowRoot) {
            visit(element.shadowRoot);
          }
        }
      };
      visit(document);
      return matches;
    };
    // Compose for Web currently hosts its Skia canvas in an open shadow root.
    // document.querySelectorAll alone would therefore report a false zero.
    const canvasElements = deepQueryAll("canvas");
    const canvases = canvasElements.slice(0, 32).map((canvas) => {
      const bounds = canvas.getBoundingClientRect();
      return {
        widthPixels: canvas.width,
        heightPixels: canvas.height,
        clientWidthPixels: canvas.clientWidth,
        clientHeightPixels: canvas.clientHeight,
        bounds: {
          x: finite(bounds.x),
          y: finite(bounds.y),
          width: finite(bounds.width),
          height: finite(bounds.height),
        },
        connected: canvas.isConnected,
      };
    });
    const nav = navigation ? {
      type: navigation.type,
      durationMillis: finite(navigation.duration),
      unloadMillis: finite(navigation.unloadEventEnd - navigation.unloadEventStart),
      redirectMillis: finite(navigation.redirectEnd - navigation.redirectStart),
      dnsMillis: finite(navigation.domainLookupEnd - navigation.domainLookupStart),
      connectMillis: finite(navigation.connectEnd - navigation.connectStart),
      tlsMillis: navigation.secureConnectionStart > 0
        ? finite(navigation.connectEnd - navigation.secureConnectionStart)
        : null,
      requestMillis: finite(navigation.responseStart - navigation.requestStart),
      ttfbMillis: finite(navigation.responseStart - navigation.requestStart),
      responseDownloadMillis: finite(navigation.responseEnd - navigation.responseStart),
      domInteractiveMillis: finite(navigation.domInteractive - navigation.startTime),
      domContentLoadedMillis: finite(navigation.domContentLoadedEventEnd - navigation.startTime),
      loadMillis: finite(navigation.loadEventEnd - navigation.startTime),
      transferSizeBytes: finite(navigation.transferSize),
      encodedBodySizeBytes: finite(navigation.encodedBodySize),
      decodedBodySizeBytes: finite(navigation.decodedBodySize),
      responseStatus: finite(navigation.responseStatus),
      nextHopProtocol: navigation.nextHopProtocol || null,
      deliveryType: navigation.deliveryType || null,
      serverTiming: Array.from(navigation.serverTiming || []).map((entry) => ({
        name: entry.name,
        durationMillis: finite(entry.duration),
        description: entry.description || null,
      })),
    } : null;

    const memory = performance.memory ? {
      jsHeapSizeLimitBytes: finite(performance.memory.jsHeapSizeLimit),
      totalJsHeapSizeBytes: finite(performance.memory.totalJSHeapSize),
      usedJsHeapSizeBytes: finite(performance.memory.usedJSHeapSize),
    } : null;

    return {
      capturedAtPerformanceMillis: finite(performance.now()),
      timeOriginUnixMillis: finite(performance.timeOrigin),
      navigation: nav,
      paints,
      firstPaintMillis: paints.find((entry) => entry.name === "first-paint")?.startTimeMillis ?? null,
      firstContentfulPaintMillis:
        paints.find((entry) => entry.name === "first-contentful-paint")?.startTimeMillis ?? null,
      resources: {
        totals: aggregateResources(resources),
        wasm: aggregateResources(resources.filter((entry) => hasSuffix(entry, ".wasm"))),
        javascript: aggregateResources(resources.filter((entry) => hasSuffix(entry, ".js"))),
        byInitiator: Object.fromEntries(
          Object.entries(byInitiator).map(([key, entries]) => [key, aggregateResources(entries)]),
        ),
        entries: resources,
      },
      document: {
        readyState: document.readyState,
        title: document.title,
        visibilityState: document.visibilityState,
        url: location.href,
      },
      viewport: {
        innerWidthPixels: window.innerWidth,
        innerHeightPixels: window.innerHeight,
        outerWidthPixels: window.outerWidth,
        outerHeightPixels: window.outerHeight,
        devicePixelRatio: finite(window.devicePixelRatio),
      },
      canvas: {
        count: canvasElements.length,
        totalBackingPixelArea: sum(canvases.map((canvas) => canvas.widthPixels * canvas.heightPixels)),
        elements: canvases,
      },
      memory,
      workers: {
        serviceWorkerControlled: Boolean(navigator.serviceWorker?.controller),
      },
    };
  });

  const waitUntilReady = async () => {
    if (probeConfig.readySelector) {
      await page.locator(probeConfig.readySelector).first().waitFor({
        state: probeConfig.readyState,
        timeout: probeConfig.timeoutMillis,
      });
    }
    if (probeConfig.settleMillis > 0) {
      await page.waitForTimeout(probeConfig.settleMillis);
    }
  };

  const navigate = async (navigationPhase) => {
    phase = navigationPhase;
    const wallStartUnixMillis = Date.now();
    const response = await page.goto(probeConfig.targetUrl, {
      waitUntil: "load",
      timeout: probeConfig.timeoutMillis,
    });
    const loadCompletedUnixMillis = Date.now();
    await waitUntilReady();
    const readyUnixMillis = Date.now();
    const snapshot = await runtimeSnapshot();
    return {
      phase: navigationPhase,
      wallNavigationMillis: loadCompletedUnixMillis - wallStartUnixMillis,
      wallReadyMillis: readyUnixMillis - wallStartUnixMillis,
      mainResponse: response ? {
        status: response.status(),
        statusText: response.statusText(),
        fromServiceWorker: response.fromServiceWorker(),
        url: response.url(),
      } : null,
      ...snapshot,
    };
  };

  const coldNavigation = await navigate("cold-navigation");
  const warmNavigation = await navigate("warm-navigation");

  phase = "frame-measurement";
  let cdpSession = null;
  let cdpBefore = null;
  let cdpAfter = null;
  let cdpPostGc = null;
  let cdpPostGcHeapUsage = null;
  let cdpGarbageCollectionSucceeded = false;
  let cdpError = null;
  try {
    cdpSession = await page.context().newCDPSession(page);
    await cdpSession.send("Performance.enable");
    cdpBefore = await cdpSession.send("Performance.getMetrics");
  } catch (error) {
    cdpError = String(error.message || error);
    cdpSession = null;
  }

  const frameMeasurement = await page.evaluate(async (config) => {
    const frameTimeoutMillis = config.frameTimeoutMillis;
    const awaitFrameTimestamps = (count) => new Promise((resolve, reject) => {
      if (count <= 0) {
        resolve([]);
        return;
      }
      const timestamps = [];
      const timeout = setTimeout(() => {
        reject(new Error(`Timed out after ${frameTimeoutMillis} ms while waiting for ${count} animation frames`));
      }, frameTimeoutMillis);
      const next = (timestamp) => {
        timestamps.push(timestamp);
        if (timestamps.length >= count) {
          clearTimeout(timeout);
          resolve(timestamps);
        } else {
          requestAnimationFrame(next);
        }
      };
      requestAnimationFrame(next);
    });

    await awaitFrameTimestamps(config.warmupFrames);

    const longTasks = [];
    let longTaskObserver = null;
    const longTaskSupported = typeof PerformanceObserver !== "undefined"
      && PerformanceObserver.supportedEntryTypes?.includes("longtask");
    if (longTaskSupported) {
      longTaskObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          longTasks.push({
            startTimeMillis: entry.startTime,
            durationMillis: entry.duration,
            name: entry.name,
          });
        }
      });
      longTaskObserver.observe({ type: "longtask" });
    }

    const heapBefore = performance.memory ? {
      jsHeapSizeLimitBytes: performance.memory.jsHeapSizeLimit,
      totalJsHeapSizeBytes: performance.memory.totalJSHeapSize,
      usedJsHeapSizeBytes: performance.memory.usedJSHeapSize,
    } : null;
    const wallStartUnixMillis = Date.now();
    const timestamps = await awaitFrameTimestamps(config.measureFrames + 1);
    const wallEndUnixMillis = Date.now();
    if (longTaskObserver) {
      for (const entry of longTaskObserver.takeRecords()) {
        longTasks.push({
          startTimeMillis: entry.startTime,
          durationMillis: entry.duration,
          name: entry.name,
        });
      }
      longTaskObserver.disconnect();
    }
    const heapAfter = performance.memory ? {
      jsHeapSizeLimitBytes: performance.memory.jsHeapSizeLimit,
      totalJsHeapSizeBytes: performance.memory.totalJSHeapSize,
      usedJsHeapSizeBytes: performance.memory.usedJSHeapSize,
    } : null;
    const intervalsMillis = timestamps.slice(1).map((timestamp, index) => timestamp - timestamps[index]);
    return {
      requestedWarmupFrames: config.warmupFrames,
      requestedMeasureFrames: config.measureFrames,
      measuredFrameCount: intervalsMillis.length,
      timestampsMillis: timestamps,
      intervalsMillis,
      wallDurationMillis: wallEndUnixMillis - wallStartUnixMillis,
      longTasksSupported: longTaskSupported,
      longTasks,
      heapBefore,
      heapAfter,
    };
  }, probeConfig);

  if (cdpSession) {
    try {
      cdpAfter = await cdpSession.send("Performance.getMetrics");
      // Keep the ordinary post-window sample above intact: it describes the natural
      // collector phase seen by the application.  This second sample is deliberately
      // separate and measures live retention after a forced full collection.
      await cdpSession.send("HeapProfiler.collectGarbage");
      cdpGarbageCollectionSucceeded = true;
      cdpPostGc = await cdpSession.send("Performance.getMetrics");
      cdpPostGcHeapUsage = await cdpSession.send("Runtime.getHeapUsage");
      await cdpSession.detach();
    } catch (error) {
      cdpError = String(error.message || error);
    }
  }

  const metricMap = (snapshot) => snapshot
    ? Object.fromEntries(snapshot.metrics.map((entry) => [entry.name, entry.value]))
    : null;
  const browser = page.context().browser();
  const browserEnvironment = await page.evaluate(() => ({
    userAgent: navigator.userAgent,
    platform: navigator.platform,
    languages: Array.from(navigator.languages || []),
    hardwareConcurrency: navigator.hardwareConcurrency || null,
    deviceMemoryGiB: navigator.deviceMemory || null,
    maxTouchPoints: navigator.maxTouchPoints,
    webdriver: navigator.webdriver,
    crossOriginIsolated: window.crossOriginIsolated,
    secureContext: window.isSecureContext,
    userAgentData: navigator.userAgentData ? {
      mobile: navigator.userAgentData.mobile,
      platform: navigator.userAgentData.platform,
      brands: Array.from(navigator.userAgentData.brands || []),
    } : null,
    screen: {
      widthPixels: screen.width,
      heightPixels: screen.height,
      availableWidthPixels: screen.availWidth,
      availableHeightPixels: screen.availHeight,
      colorDepthBits: screen.colorDepth,
      pixelDepthBits: screen.pixelDepth,
    },
    capabilities: {
      webAssembly: typeof WebAssembly !== "undefined",
      webGpu: Boolean(navigator.gpu),
      performanceMemory: Boolean(performance.memory),
      longTasks: typeof PerformanceObserver !== "undefined"
        && PerformanceObserver.supportedEntryTypes?.includes("longtask"),
    },
  }));

  return {
    schemaVersion: 2,
    targetUrl: probeConfig.targetUrl,
    browser: {
      name: browser ? browser.browserType().name() : null,
      version: browser ? browser.version() : null,
      ...browserEnvironment,
    },
    coldNavigation,
    warmNavigation,
    frameMeasurement,
    cdp: {
      supported: Boolean(cdpBefore && cdpAfter),
      postGcSupported: Boolean(
        cdpGarbageCollectionSucceeded && cdpPostGc && cdpPostGcHeapUsage,
      ),
      error: cdpError,
      before: metricMap(cdpBefore),
      after: metricMap(cdpAfter),
      postGc: metricMap(cdpPostGc),
      postGcHeapUsage: cdpPostGcHeapUsage,
      postGcCollection: {
        method: "HeapProfiler.collectGarbage",
        passes: cdpGarbageCollectionSucceeded ? 1 : 0,
        succeeded: cdpGarbageCollectionSucceeded,
      },
    },
    diagnostics: {
      consoleMessages,
      pageErrors,
      requestFailures,
      httpErrors,
      workerCount: page.workers().length,
    },
  };
}
