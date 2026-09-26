// Shared visual editor: wraps Home Assistant's own <ha-form> so each card
// only has to describe its fields. `schema(config, hass)` returns an ha-form
// schema (it can vary with the current config and home, e.g. light vs room),
// `labels`/`helpers` map field names to label and helper text, and
// `normalize(config)` can upgrade older config shapes before they're shown.
// Optional: `fill(config, hass)` adds defaults once the home is known (saved
// straight into the config), `display(config, hass)` adds form-only fields
// (e.g. list labels) and `store(value)` takes them back out.

const same = (c) => c;

export function createFormEditor({ schema, labels = {}, helpers = {}, normalize = same, fill = same, display = same, store = same }) {
  return class extends HTMLElement {
    setConfig(config) {
      this._config = normalize(config || {});
      this._render();
    }

    set hass(hass) {
      this._hass = hass;
      this._render();
    }

    _render() {
      // HA may hand over hass before the config (or vice versa); wait for both.
      if (!this._hass || !this._config) return;
      if (!this._form) {
        this._form = document.createElement('ha-form');
        this._form.addEventListener('value-changed', (ev) => this._changed(store(ev.detail.value)));
        this.appendChild(this._form);
      }
      const filled = fill(this._config, this._hass);
      if (filled !== this._config) {
        this._changed(filled);
        return;
      }
      this._form.hass = this._hass;
      this._form.data = display(this._config, this._hass);
      this._form.schema = schema(this._config, this._hass);
      this._form.computeLabel = (s) => labels[s.name] || s.title || s.name;
      this._form.computeHelper = (s) => helpers[s.name];
    }

    _changed(config) {
      this._config = config;
      this.dispatchEvent(new CustomEvent('config-changed', { detail: { config }, bubbles: true, composed: true }));
      this._render();
    }
  };
}
