# Basic Ring Handler in Azure

First a project needs to include hedge and it's built tools for boot as well as cljs compiler.

<pre>
  <code class="language-clojure" concordion:echo="#boot = simpleBoot()" concordion:set="#boot"/>
</pre>


Then if we have a basic ring handler in source file src/[_ns-name_](- "c:echo=handlerNsName()").cljs

<pre>
  <code class="language-clojure" concordion:echo="basicHandlerNS()"/>
</pre>

And hedge configuration file mapping this handler to a url in resources/hedge.edn

<pre>
  <code class="language-clojure" concordion:echo="basicHelloConf()"/>
</pre>

running

<pre>
  <code class="language-bash" concordion:execute="deploy(#boot)">
    boot azure
  </code>
</pre>

deploys the hedge application into azure.

after that url [app-url](- "#url") " will answer with

<pre>
  <code class="language-bash" concordion:assert-equals="getresource(#url)">
    "hello"
  </code>
</pre>

