// Refuses cookie consent on the consent tools French shops use, through their own API when they
// expose one, else by clicking their "reject all" / "continue without accepting" button. Runs in
// every frame (some tools draw their banner in an iframe), for 20 s after the page starts.
(function () {
  var apis = [
    function () { if (window.Didomi && Didomi.setUserDisagreeToAll && Didomi.notice && Didomi.notice.isVisible && Didomi.notice.isVisible()) { Didomi.setUserDisagreeToAll(); return true; } },
    function () { if (window.OneTrust && OneTrust.RejectAll && document.querySelector('#onetrust-banner-sdk:not([style*="display: none"])')) { OneTrust.RejectAll(); return true; } },
    function () { if (window.Cookiebot && Cookiebot.submitCustomConsent && !Cookiebot.hasResponse) { Cookiebot.submitCustomConsent(false, false, false); return true; } },
    function () { if (window.UC_UI && UC_UI.denyAllConsents && UC_UI.isConsentRequired && UC_UI.isConsentRequired()) { UC_UI.denyAllConsents().then(function () { UC_UI.closeCMP && UC_UI.closeCMP(); }); return true; } }
  ];
  var buttons = [
    '#sp-cc-rejectall-link',                       // Amazon
    '#onetrust-reject-all-handler',                // OneTrust
    '#didomi-notice-disagree-button',              // Didomi
    '.didomi-continue-without-agreeing',
    '#axeptio_btn_dismiss',                        // Axeptio
    '#popin_tc_privacy_button_2',                  // TrustCommander
    '#footer_tc_privacy_button_2',
    '#CybotCookiebotDialogBodyButtonDecline',      // Cookiebot
    '#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll',
    'button[data-testid="uc-deny-all-button"]',    // Usercentrics
    '#truste-consent-required',                    // TrustArc
    '.qc-cmp2-summary-buttons button[mode="secondary"]', // Quantcast
    '.sp_choice_type_REJECT_ALL',                  // Sourcepoint (in its iframe)
    '#cmpwelcomebtnno',                            // consentmanager.net
    '.cky-btn-reject',                             // CookieYes
    '#tarteaucitronAllDenied2',                    // tarteaucitron
    '.cc-btn.cc-deny'                              // Cookie Consent (Osano)
  ];
  var done = false, tries = 0;
  function attempt() {
    if (done) return;
    for (var i = 0; i < apis.length; i++) {
      try { if (apis[i]()) { done = true; return; } } catch (e) {}
    }
    for (var j = 0; j < buttons.length; j++) {
      var b = document.querySelector(buttons[j]);
      if (b) { try { b.click(); done = true; return; } catch (e) {} }
    }
  }
  var timer = setInterval(function () {
    attempt();
    if (done || ++tries > 40) clearInterval(timer);
  }, 500);
  document.addEventListener('DOMContentLoaded', attempt);
})();
