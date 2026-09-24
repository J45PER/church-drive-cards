// Shared visual editor: wraps Home Assistant's own <ha-form> so each card
// only has to describe its fields. `schema(config)` returns an ha-form schema
// (it can vary with the current config, e.g. light mode vs room mode),
// `labels`/`helpers` map field names to label and helper text, and
// `normalize(config)` can upgrade older config shapes before they're shown.

export function createFormEditor({ schema, labels = {}, helpers = {}, normalize = (c) => c }) {
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
        this._form.addEventListener('value-changed', (ev) => {
          this._config = ev.detail.value;
          this.dispatchEvent(
            new CustomEvent('config-changed', { detail: { config: this._config }, bubbles: true, composed: true })
          );
          this._render();
        });
        this.appendChild(this._form);
      }
      this._form.hass = this._hass;
      this._form.data = this._config;
      this._form.schema = schema(this._config);
      this._form.computeLabel = (s) => labels[s.name] || s.title || s.name;
      this._form.computeHelper = (s) => helpers[s.name];
    }
  };
}
