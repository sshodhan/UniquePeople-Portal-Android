(function () {
  var video = document.getElementById("preview");
  var status = document.getElementById("status");
  var result = document.getElementById("result");
  var detector = null;
  var stream = null;
  var scanning = false;
  var lastScan = 0;

  function setStatus(message) {
    status.textContent = message;
  }

  function reportError(message) {
    setStatus(message);
    if (window.UniquePeopleQr && window.UniquePeopleQr.onScannerError) {
      window.UniquePeopleQr.onScannerError(message);
    }
  }

  function stopCamera() {
    if (!stream) return;
    stream.getTracks().forEach(function (track) {
      track.stop();
    });
    stream = null;
  }

  async function start() {
    try {
      if (!("mediaDevices" in navigator) || !navigator.mediaDevices.getUserMedia) {
        reportError("Camera access is not available on this Portal WebView.");
        return;
      }
      if (!("BarcodeDetector" in window)) {
        reportError("QR scanning is not available on this Portal WebView. Paste the link in Settings.");
        return;
      }

      detector = new BarcodeDetector({ formats: ["qr_code"] });
      stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
          facingMode: { ideal: "environment" },
          width: { ideal: 1280 },
          height: { ideal: 720 }
        }
      });
      video.srcObject = stream;
      await video.play();
      scanning = true;
      setStatus("Point the Portal camera at a Google Photos QR code.");
      requestAnimationFrame(scan);
    } catch (error) {
      reportError("Could not start the camera. Allow camera access, then try again.");
    }
  }

  async function scan(now) {
    if (!scanning) return;
    if (now - lastScan < 250) {
      requestAnimationFrame(scan);
      return;
    }
    lastScan = now;

    try {
      var codes = await detector.detect(video);
      if (codes && codes.length > 0) {
        var value = (codes[0].rawValue || "").trim();
        if (value) {
          scanning = false;
          stopCamera();
          result.textContent = value;
          setStatus("QR code found. Saving link...");
          if (window.UniquePeopleQr && window.UniquePeopleQr.onQrCode) {
            window.UniquePeopleQr.onQrCode(value);
          }
          return;
        }
      }
    } catch (error) {
      reportError("Could not read the QR code. Try better lighting or paste the link in Settings.");
      scanning = false;
      stopCamera();
      return;
    }
    requestAnimationFrame(scan);
  }

  window.addEventListener("beforeunload", stopCamera);
  start();
})();
