/**
 * Shared admin-gate helper.
 *
 * The Dashboard nav link is hidden by default on every page. It only
 * appears once the admin ID has been verified (via the gate on the
 * dashboard page itself) for the current browser tab. This is a simple
 * UI gate, not real authentication — the dashboard page itself re-checks
 * verification before showing any data, so hiding the link is just a
 * convenience to keep it out of the way for non-admins.
 */
(function () {
    var SESSION_KEY = 'qrAttendanceAdminVerified';

    function isAdminVerified() {
        return sessionStorage.getItem(SESSION_KEY) === 'true';
    }

    function setAdminVerified() {
        sessionStorage.setItem(SESSION_KEY, 'true');
    }

    function clearAdminVerified() {
        sessionStorage.removeItem(SESSION_KEY);
    }

    function applyNavVisibility() {
        var dashLinks = document.querySelectorAll('[data-nav="dashboard"]');
        dashLinks.forEach(function (el) {
            el.style.display = isAdminVerified() ? '' : 'none';
        });
    }

    document.addEventListener('DOMContentLoaded', applyNavVisibility);

    window.AdminGate = {
        isAdminVerified: isAdminVerified,
        setAdminVerified: setAdminVerified,
        clearAdminVerified: clearAdminVerified,
        applyNavVisibility: applyNavVisibility
    };
})();
