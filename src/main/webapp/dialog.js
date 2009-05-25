/*
The MIT License

Copyright (c) 2004-2009, Sun Microsystems, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

// stores a reference to the dialog
translation.dialog = null;

translation.launchDialog = function() {
    this.dialog.center();
    this.dialog.show();
};

translation.post = function(link,lang,onSuccess) {
    new Ajax.Request(rootURL+"/descriptor/hudson.plugins.translation.L10nDecorator/"+link, {
        method:"post",
        requestHeaders:{"Accept-Language":lang},
        parameters:{bundles:translation.bundles},
        onSuccess: onSuccess });
};

// instantiate the Dialog
translation.createDialog = function() {
    var d = $("l10n-dialog");
    this.post("dialog","ja",function(rsp) {
        // populate the dialog
        d.innerHTML = rsp.responseText;

        translation.dialog = new YAHOO.widget.Dialog(d, {
            width : "40em",
            visible : false,
            draggable: true,
            constraintoviewport: true,
            buttons : [
                { text:"Submit", handler:function() {
                    this.submit();
                }, isDefault:true },
                { text:"Cancel", handler:function() {
                    this.cancel();
                } }
            ]
        });
        translation.dialog.render();
        translation.launchDialog();
    });
};

// called when locale selection combo box is updated.
// make an AJAX call to the server to fetch the locale specific list.
translation.reload = function(sel) {
    this.post("text",sel.value,function(rsp) {
        $('l10n-main').innerHTML = rsp.responseText;
    });
};

translation.createDialog();