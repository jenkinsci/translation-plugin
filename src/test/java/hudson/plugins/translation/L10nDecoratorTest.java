/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.translation;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@WithJenkins
class L10nDecoratorTest {

    @Test
    @Issue("SECURITY-507")
    void ensurePostIsRequiredForSubmit(JenkinsRule j) throws Exception {
        j.jenkins.setCrumbIssuer(null);
        JenkinsRule.WebClient wc = j.createWebClient();

        L10nDecorator l10nDecorator = j.jenkins.getDescriptorByType(L10nDecorator.class);
        String submitUrl = l10nDecorator.getUrl() + "/submit";
        URL absoluteSubmitUrl = URI.create(j.getURL() + submitUrl).toURL();

        // the GET method is not allowed
        try {
            wc.goTo(submitUrl);
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(HttpURLConnection.HTTP_BAD_METHOD, e.getStatusCode());
        }

        // the POST method must be used to submit a form
        WebRequest submitRequest = new WebRequest(absoluteSubmitUrl, HttpMethod.POST);
        submitRequest.setRequestParameters(List.of(
                new NameValuePair("json", "{}"),
                new NameValuePair("locale", "fr")
        ));
        Page successSubmit = wc.getPage(submitRequest);
        assertEquals(HttpURLConnection.HTTP_OK, successSubmit.getWebResponse().getStatusCode());
    }
}
