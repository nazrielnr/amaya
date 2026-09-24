package com.amaya.intelligence.impl.local.browser

import org.json.JSONObject

object DomInspector {
    private const val BODY_MARKER = "/*__AMAYA_BODY__*/"
    @Volatile private var baseInspectorTemplate: String? = null

    fun installBaseInspectorTemplate(template: String) {
        require(BODY_MARKER in template) { "DOM inspector asset is missing its body marker" }
        baseInspectorTemplate = template
    }

    fun getDomScript(): String = baseInspector("return JSON.stringify(collectDom());")

    fun getVisibleTextScript(): String = """
        (function() {
          return document.body ? document.body.innerText.slice(0, 50000) : '';
        })();
    """.trimIndent()

    /**
     * Scheme G: cheap page fingerprint used to verify that a mutation action (click/type/…) actually
     * took effect. Captures navigation (url/title), content (body innerText length) and focus state
     * (active element tag + live value length — so typing into an input is detected even though the
     * value never appears in innerText). Compared for equality before/after an action.
     */
    fun getFingerprintScript(): String = """
        (function() {
          var a = document.activeElement;
          var live = a ? (a.value != null ? String(a.value) : (a.textContent || '')) : '';
          return JSON.stringify({
            url: location.href,
            title: document.title,
            bodyLen: document.body ? document.body.innerText.length : 0,
            activeTag: a ? a.tagName : '',
            activeValueLen: live.length
          });
        })();
    """.trimIndent()

    fun findElementScript(query: String): String {
        val q = JSONObject.quote(query)
        return baseInspector(
            """
            var query = $q;
            var found = findElement(query);
            return JSON.stringify(found || null);
            """.trimIndent()
        )
    }

    fun selectorExistsScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector(
            """
            var el = resolveElement($s);
            return JSON.stringify(el ? summarize(el) : null);
            """.trimIndent()
        )
    }

    fun fileInputConstraintsScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector("""
            var el = resolveElement($s);
            if (!el || String(el.type || '').toLowerCase() !== 'file') return JSON.stringify({ok:false});
            return JSON.stringify({ok:true, accept:(el.accept || '').split(',').map(function(x){return x.trim()}).filter(Boolean), multiple:!!el.multiple});
        """.trimIndent())
    }

    fun findTextScript(query: String): String {
        val q = JSONObject.quote(query)
        return """
            (function() {
              var needle = String($q).trim();
              if (!needle) return JSON.stringify({matches:[], total:0});
              var folded = needle.toLocaleLowerCase();
              var matches = [];
              var walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT);
              var node;
              while ((node = walker.nextNode()) && matches.length < 100) {
                var value = (node.nodeValue || '').replace(/\s+/g, ' ').trim();
                var index = value.toLocaleLowerCase().indexOf(folded);
                if (index < 0) continue;
                var parent = node.parentElement;
                if (!parent || !parent.getClientRects().length || getComputedStyle(parent).display === 'none' || getComputedStyle(parent).visibility === 'hidden') continue;
                var rect = parent.getBoundingClientRect();
                matches.push({
                  text: value.slice(Math.max(0, index - 100), Math.min(value.length, index + needle.length + 180)),
                  selector: (parent.id ? '#' + CSS.escape(parent.id) : parent.tagName.toLowerCase()),
                  tag: parent.tagName.toLowerCase(),
                  bounds: {x:Math.round(rect.left), y:Math.round(rect.top), width:Math.round(rect.width), height:Math.round(rect.height)},
                  in_viewport: rect.bottom > 0 && rect.right > 0 && rect.top < innerHeight && rect.left < innerWidth
                });
              }
              return JSON.stringify({query:needle, matches:matches, total:matches.length, truncated:matches.length >= 100});
            })();
        """.trimIndent()
    }

    fun clickPreflightScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector(
            """
            var el = resolveElement($s);
            if (!el) return JSON.stringify({ ok:false, error:'Element not found', selector:$s });
            el = clickTargetFor(el);
            if (!visible(el)) return JSON.stringify({ ok:false, error:'Element is not visible', selector:$s, element:summarize(el), href:(el.href || '') });
            if (!enabledState(el)) return JSON.stringify({ ok:false, error:'Element is disabled', selector:$s, element:summarize(el), href:(el.href || '') });
            el.scrollIntoView({block:'center', inline:'center', behavior:'auto'});
            var rect = el.getBoundingClientRect();
            var cx = Math.max(0, Math.min(window.innerWidth - 1, rect.left + rect.width / 2));
            var cy = Math.max(0, Math.min(window.innerHeight - 1, rect.top + rect.height / 2));
            var rootAtPoint = el.getRootNode && el.getRootNode();
            var top = rootAtPoint && rootAtPoint.elementFromPoint ? rootAtPoint.elementFromPoint(cx, cy) : document.elementFromPoint(cx, cy);
            var covered = !!(top && top !== el && !el.contains(top) && !top.contains(el));
            var anchor = el.closest ? el.closest('a[href]') : null;
            return JSON.stringify({
              ok:true,
              element:summarize(el),
              click:{x:cx,y:cy},
              in_viewport: rect.bottom > 0 && rect.right > 0 && rect.top < window.innerHeight && rect.left < window.innerWidth,
              covered: covered,
              blocker: covered ? summarize(top) : null,
              href: el.href || (anchor ? anchor.href : '') || '',
              target: el.getAttribute('target') || (anchor ? (anchor.getAttribute('target') || '') : ''),
              tag: (el.tagName || '').toLowerCase(),
              submits_form: !!(el.form && ((el.tagName || '').toLowerCase() === 'button' || /^(submit|image)$/i.test(el.type || '')))
            });
            """.trimIndent()
        )
    }

    fun clickScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector(
            """
            var info = JSON.parse((function() {
              var el = resolveElement($s);
              if (!el) return JSON.stringify({ ok:false, error:'Element not found', selector:$s });
              el = clickTargetFor(el);
              if (!visible(el)) return JSON.stringify({ ok:false, error:'Element is not visible', selector:$s, element:summarize(el), href:(el.href || '') });
              if (!enabledState(el)) return JSON.stringify({ ok:false, error:'Element is disabled', selector:$s, element:summarize(el), href:(el.href || '') });
              el.scrollIntoView({block:'center', inline:'center', behavior:'auto'});
              var rect = el.getBoundingClientRect();
              var cx = Math.max(0, Math.min(window.innerWidth - 1, rect.left + rect.width / 2));
              var cy = Math.max(0, Math.min(window.innerHeight - 1, rect.top + rect.height / 2));
              var rootAtPoint = el.getRootNode && el.getRootNode();
              var top = rootAtPoint && rootAtPoint.elementFromPoint ? rootAtPoint.elementFromPoint(cx, cy) : document.elementFromPoint(cx, cy);
              var covered = !!(top && top !== el && !el.contains(top) && !top.contains(el));
              var anchor = el.closest ? el.closest('a[href]') : null;
              return JSON.stringify({ ok:true, element:summarize(el), click:{x:cx,y:cy}, covered:covered, blocker:covered ? summarize(top) : null, href:el.href || (anchor ? anchor.href : '') || '' });
            })());
            if (!info.ok) return JSON.stringify(info);
            if (info.covered) {
              return JSON.stringify({ ok:false, error:'Element is covered by another element', selector:$s, blocker:info.blocker, element:info.element, href:info.href });
            }
            var el = resolveElement($s);
            if (!el) return JSON.stringify({ ok:false, error:'Element not found after scroll', selector:$s });
            el = clickTargetFor(el);
            var cx = info.click.x;
            var cy = info.click.y;
            el.focus && el.focus();
            // HTMLElement.click preserves browser default actions, including form submit.
            // Synthetic MouseEvent("click") only runs listeners and leaves submit buttons inert.
            if (typeof el.click === 'function') el.click();
            else el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true, view:window, clientX:cx, clientY:cy}));
            return JSON.stringify({ ok:true, element:summarize(el), click:{x:cx,y:cy}, strategy:'dom_click_fallback', href:info.href });
            """.trimIndent()
        )
    }

    fun hoverScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector(
            """
            var el = resolveElement($s);
            if (!el) return JSON.stringify({ ok:false, error:'Element not found', selector:$s });
            el.scrollIntoView({block:'center', inline:'center', behavior:'auto'});
            var rect = el.getBoundingClientRect();
            var x = rect.left + rect.width / 2;
            var y = rect.top + rect.height / 2;
            ['pointerover','mouseover','mouseenter','mousemove'].forEach(function(type) {
              var Ctor = type.indexOf('pointer') === 0 && window.PointerEvent ? PointerEvent : MouseEvent;
              el.dispatchEvent(new Ctor(type, {bubbles:true,cancelable:true,view:window,clientX:x,clientY:y,pointerType:'mouse'}));
            });
            return JSON.stringify({ ok:true, element:summarize(el) });
            """.trimIndent()
        )
    }

    fun selectOptionScript(selector: String, value: String): String {
        val s = JSONObject.quote(selector)
        val v = JSONObject.quote(value)
        return baseInspector(
            """
            var el = resolveElement($s);
            if (!el) return JSON.stringify({ ok:false, error:'Select not found', selector:$s });
            if ((el.tagName || '').toLowerCase() !== 'select') return JSON.stringify({ ok:false, error:'Element is not a select', selector:$s, element:summarize(el) });
            var wanted = String($v);
            var option = Array.prototype.find.call(el.options, function(o) { return o.value === wanted || (o.textContent || '').trim() === wanted; });
            if (!option) return JSON.stringify({ ok:false, error:'Option not found', selector:$s });
            var setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value');
            if (setter && setter.set) setter.set.call(el, option.value); else el.value = option.value;
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
            return JSON.stringify({ ok:true, element:summarize(el), value:option.value, text:(option.textContent || '').trim() });
            """.trimIndent()
        )
    }

    fun focusScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector(
            """
            var el = resolveElement($s);
            if (!el) return JSON.stringify({ ok:false, error:'Element not found', selector:$s });
            el = focusTargetFor(el);
            el.scrollIntoView({block:'center', inline:'center', behavior:'auto'});
            el.focus && el.focus();
            return JSON.stringify({ ok:true, element:summarize(el) });
            """.trimIndent()
        )
    }

    fun boundsScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector(
            """
            var el = resolveElement($s);
            if (!el) return JSON.stringify({ ok:false, error:'Element not found', selector:$s });
            el = clickTargetFor(el);
            var rect = el.getBoundingClientRect();
            return JSON.stringify({ ok:true, x:rect.left, y:rect.top, width:rect.width, height:rect.height, center_x:rect.left + rect.width/2, center_y:rect.top + rect.height/2, element:summarize(el) });
            """.trimIndent()
        )
    }

    fun enterFallbackScript(): String = baseInspector(
        """
        var el = document.activeElement;
        if (el) {
          ['keydown','keypress','keyup'].forEach(function(type) {
            el.dispatchEvent(new KeyboardEvent(type, {bubbles:true, cancelable:true, key:'Enter', code:'Enter', keyCode:13, which:13}));
          });
          if (el.form && typeof el.form.requestSubmit === 'function') el.form.requestSubmit();
          else if (el.form) el.form.submit();
        }
        return JSON.stringify({ ok:true, active: el ? summarize(el) : null });
        """.trimIndent()
    )

    fun submitFromContextScript(selector: String?): String {
        val s = selector?.let { JSONObject.quote(it) } ?: "null"
        return baseInspector(
            """
            var anchor = $s ? resolveElement($s) : document.activeElement;
            if (!anchor) return JSON.stringify({ ok:false, error:'No active or target element available for submit' });
            var purpose = inferSourcePurpose(anchor);
            var button = nearestActionButton(anchor, purpose) || nearestActionButton(anchor, 'submit');
            if (button) {
              button.scrollIntoView({block:'center', inline:'center', behavior:'auto'});
              button.focus && button.focus();
              if (typeof button.click === 'function') button.click();
              else button.dispatchEvent(new MouseEvent('click', { bubbles:true, cancelable:true, view:window }));
              return JSON.stringify({ ok:true, strategy:'related_button', purpose:purpose, button:summarize(button), anchor:summarize(anchor) });
            }
            if (anchor.form && typeof anchor.form.requestSubmit === 'function') {
              anchor.form.requestSubmit();
              return JSON.stringify({ ok:true, strategy:'form_request_submit', purpose:purpose, anchor:summarize(anchor) });
            }
            if (anchor.form) {
              anchor.form.submit();
              return JSON.stringify({ ok:true, strategy:'form_submit', purpose:purpose, anchor:summarize(anchor) });
            }
            ['keydown','keypress','keyup'].forEach(function(type) {
              try {
                anchor.dispatchEvent(new KeyboardEvent(type, {bubbles:true, cancelable:true, key:'Enter', code:'Enter', keyCode:13, which:13}));
              } catch (e) {
                anchor.dispatchEvent(new Event(type, { bubbles:true, cancelable:true }));
              }
            });
            return JSON.stringify({ ok:true, strategy:'enter_fallback', purpose:purpose, anchor:summarize(anchor) });
            """.trimIndent()
        )
    }

    fun findSearchInputScript(): String = baseInspector(
        """
        var nodes = Array.prototype.slice.call(document.querySelectorAll('input,textarea,[contenteditable],[role=textbox]')).filter(function(el) {
          return visible(el) && enabledState(el);
        });
        var best = null;
        var bestScore = -1;
        nodes.forEach(function(el) {
          var score = scoreSearchInput(el);
          if (score > bestScore) {
            bestScore = score;
            best = el;
          }
        });
        return JSON.stringify(best ? (summarize(best).element_id) : null);
        """.trimIndent()
    )

    fun typeScript(selector: String?, text: String, append: Boolean): String {
        val s = selector?.let(JSONObject::quote) ?: "null"
        val t = JSONObject.quote(text)
        return baseInspector(
            """
            var el = $s ? resolveElement($s) : document.activeElement;
            if (!el) return JSON.stringify({ ok:false, error:'No focused editable element' });
            el = focusTargetFor(el);
            if (!isEditable(el)) return JSON.stringify({ ok:false, error:'Focused element is not editable', selector:$s, element:summarize(el) });
            focusEditable(el);
            var value = $t;
            if (!${append}) clearEditable(el);
            var result = insertTextLikeUser(el, value, true);
            return JSON.stringify({ ok:true, element:summarize(el), typed:value.length, strategy:result.strategy, value_after:result.value });
            """.trimIndent()
        )
    }

    fun clearScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector(
            """
            var el = resolveElement($s);
            if (!el) return JSON.stringify({ ok:false, error:'Input not found', selector:$s });
            if (!isEditable(el)) return JSON.stringify({ ok:false, error:'Element is not editable', selector:$s, element:summarize(el) });
            focusEditable(el);
            var result = clearEditable(el);
            return JSON.stringify({ ok:true, element:summarize(el), strategy:result.strategy, value_after:result.value });
            """.trimIndent()
        )
    }

    fun fileInputAssignStartScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector("""
            var el = resolveElement($s);
            if (!el || String(el.type || '').toLowerCase() !== 'file') return JSON.stringify({ok:false,error:'File input not found'});
            window.__amayaUpload = {selector:$s, files:[]};
            return JSON.stringify({ok:true});
        """.trimIndent())
    }

    fun fileInputAssignChunkScript(name: String, mime: String, lastModified: Long, chunkBase64: String, first: Boolean): String = baseInspector("""
        var state = window.__amayaUpload;
        if (!state) return JSON.stringify({ok:false,error:'Upload state missing'});
        if (${if (first) "true" else "false"}) state.files.push({name:${JSONObject.quote(name)},mime:${JSONObject.quote(mime)},lastModified:$lastModified,chunks:[]});
        var encoded = ${JSONObject.quote(chunkBase64)};
        var binary = atob(encoded);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        state.files[state.files.length - 1].chunks.push(bytes);
        return JSON.stringify({ok:true});
    """.trimIndent())

    fun fileInputAssignFinishScript(): String = baseInspector("""
        var state = window.__amayaUpload;
        if (!state) return JSON.stringify({ok:false,error:'Upload state missing'});
        var el = resolveElement(state.selector);
        if (!el || String(el.type || '').toLowerCase() !== 'file') return JSON.stringify({ok:false,error:'File input not found'});
        var data = new DataTransfer();
        state.files.forEach(function(item) {
          data.items.add(new File(item.chunks, item.name, {type:item.mime,lastModified:item.lastModified || Date.now()}));
        });
        el.files = data.files;
        el.dispatchEvent(new Event('input', {bubbles:true}));
        el.dispatchEvent(new Event('change', {bubbles:true}));
        delete window.__amayaUpload;
        return JSON.stringify({ok:true,file_names:Array.prototype.map.call(el.files, function(file){return file.name})});
    """.trimIndent())

    fun fileInputClickScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector("""
            var el = resolveElement($s);
            if (!el || String(el.type || '').toLowerCase() !== 'file') return JSON.stringify({ok:false,error:'File input not found'});
            if (typeof el.click === 'function') el.click(); else return JSON.stringify({ok:false,error:'File input is not clickable'});
            return JSON.stringify({ok:true});
        """.trimIndent())
    }

    fun elementSensitiveScript(selector: String): String {
        val s = JSONObject.quote(selector)
        return baseInspector(
            """
            var el = resolveElement($s);
            return JSON.stringify(el ? summarize(el) : null);
            """.trimIndent()
        )
    }

    fun elementStateScript(selector: String?): String {
        val s = selector?.let(JSONObject::quote) ?: "null"
        return baseInspector(
            """
            var el = $s ? resolveElement($s) : document.activeElement;
            if (el) el = focusTargetFor(el);
            if (!el) return JSON.stringify({ ok:false, exists:false, selector:$s });
            var active = document.activeElement;
            var root = el.getRootNode && el.getRootNode();
            var mutationRoot = root === document ? document.documentElement : root;
            return JSON.stringify({
              ok:true,
              exists:true,
              selector:selectorFor(el),
              visible:visible(el),
              enabled:enabledState(el),
              focused:active === el || !!(el.contains && active && el.contains(active)),
              checked:el.checked === true || el.getAttribute('aria-checked') === 'true',
              expanded:el.getAttribute('aria-expanded') === 'true',
              editable:isEditable(el),
              clickable:/button|a/i.test(el.tagName || '') || !!el.onclick || el.getAttribute('role') === 'button',
              type:(el.type || '').toLowerCase(),
              file_count:el.files ? el.files.length : 0,
              file_names:el.files ? Array.prototype.map.call(el.files, function(file){return file.name}) : [],
              value:currentEditableValue(el).slice(0, 400),
              text:textForElement(el).trim().replace(/\s+/g, ' ').slice(0, 400),
              mutation_version:Number((mutationRoot && mutationRoot.__amayaMutationVersion) || 0)
            });
            """.trimIndent()
        )
    }

    private fun baseInspector(body: String): String =
        (baseInspectorTemplate ?: FALLBACK_INSPECTOR_TEMPLATE)
            .replace(BODY_MARKER, body)

    private const val FALLBACK_INSPECTOR_TEMPLATE = """
        (function() {
          function textForElement(el) { return (el && (el.innerText || el.textContent || '')).trim(); }
          function visible(el) { return !!el && !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length); }
          function enabledState(el) { return !!el && !el.disabled && el.getAttribute('aria-disabled') !== 'true'; }
          function isEditable(el) { return !!el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)); }
          function selectorFor(el) { return el && el.id ? '#' + CSS.escape(el.id) : el && el.tagName ? el.tagName.toLowerCase() : ''; }
          function summarize(el) { return {tag:el.tagName, text:textForElement(el).slice(0,400), selector:selectorFor(el)}; }
          function resolveElement(selector) { try { return document.querySelector(selector); } catch (_) { return null; } }
          function findElement(query) { var q=String(query||'').toLowerCase(); return Array.prototype.find.call(document.querySelectorAll('button,a,input,textarea,select,[role="button"]'), function(el) { return textForElement(el).toLowerCase().indexOf(q) >= 0; }); }
          function collectDom() { return {title:document.title, url:location.href, text:textForElement(document.body).slice(0,50000)}; }
          /*__AMAYA_BODY__*/
        })();
    """
}
