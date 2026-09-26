// Shared visual editor: wraps Home Assistant's own <ha-form> so each card
// only has to describe its fields. `schema(config, hass)` returns an ha-form
// schema (it can vary with the current config and home, e.g. light vs room),
// `labels`/`helpers` map field names to label and helper text, and
// `normalize(config)` can upgrade older config shapes before they're shown.
// Optional: `fill(config, hass)` adds defaults once the home is known (saved
// straight into the config), `display(config, hass)` adds form-only fields
// (e.g. list labels) and `store(value)` takes them back out. `buttons`
// ([{ label, apply(config), field?, variant? }]) sit on the Add row of the list
// field `field` (left of Add), or under the form; their result goes through
// `fill` before it's saved.

const same = (c) => c;

// The <ha-selector> for a form field, looking through shadow roots.
function findSelector(root, name, depth = 0) {
  if (!root || depth > 6) return null;
  for (const el of root.querySelectorAll('*')) {
    if (el.localName === 'ha-selector' && el.name === name) return el;
    const found = el.shadowRoot && findSelector(el.shadowRoot, name, depth + 1);
    if (found) return found;
  }
  return null;
}

// Put a button on a list field's Add row: it goes left, Add goes right.
function placeInList(form, field, button) {
  const selector = findSelector(form.shadowRoot, field);
  const list = selector && selector.shadowRoot && selector.shadowRoot.querySelector('ha-selector-object');
  const container = list && list.shadowRoot && list.shadowRoot.querySelector('.items-container');
  if (!container) return false;
  if (button.parentNode === container) return true;
  if (!list.shadowRoot.querySelector('style.cd-row')) {
    const style = document.createElement('style');
    style.className = 'cd-row';
    style.textContent =
      '.items-container{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;row-gap:8px}' +
      '.items-container>ha-sortable{flex-basis:100%}.items-container>.cd-button{order:1}.items-container>ha-button:not(.cd-button){order:2}';
    list.shadowRoot.appendChild(style);
  }
  button.style.marginTop = '';
  container.appendChild(button);
  return true;
}

export function createFormEditor({ schema, labels = {}, helpers = {}, normalize = same, fill = same, display = same, store = same, buttons = [] }) {
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
        this._buttons = buttons.map(({ label, apply, field, variant }) => {
          const button = document.createElement('ha-button');
          button.className = 'cd-button';
          button.textContent = label;
          button.setAttribute('appearance', 'filled');
          if (variant) button.setAttribute('variant', variant);
          button.addEventListener('click', () => this._changed(fill(apply(this._config), this._hass)));
          return { button, field };
        });
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
      this._placeButtons();
    }

    // HA renders the form's insides asynchronously: try for a couple of
    // seconds to reach the list's Add row, else put the button under the form.
    _placeButtons(tries = 0) {
      clearTimeout(this._placeTimer);
      const waiting = (this._buttons || []).filter(({ button, field }) => !(field && placeInList(this._form, field, button)));
      if (!waiting.length) return;
      if (tries < 20) {
        this._placeTimer = setTimeout(() => this._placeButtons(tries + 1), 100);
        return;
      }
      waiting.forEach(({ button }) => {
        if (button.parentNode === this) return;
        button.style.marginTop = '16px';
        this.appendChild(button);
      });
    }

    _changed(config) {
      this._config = config;
      this.dispatchEvent(new CustomEvent('config-changed', { detail: { config }, bubbles: true, composed: true }));
      this._render();
    }
  };
}
