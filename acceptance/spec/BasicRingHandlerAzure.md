# Basic Ring Handler in Azure

First a project needs to include hedge and it's built tools for boot as well as cljs compiler.

<pre>
  <code class="language-clojure" concordion:echo="#boot = simpleBoot()" concordion:set="#boot"/>
</pre>

The project also has to explicitly set Clojure version to ensure compatibility:

<pre>
  <code class="language-clojure" concordion:echo="#props = bootProps()" concordion:set="#props"/>
</pre>

Then if we have a basic ring handler in source file src/[_ns-name_](- "c:echo=handlerNsName()")/core.cljs

<pre>
  <code class="language-clojure" concordion:echo="basicHandlerNS()"/>
</pre>

And hedge configuration file mapping this handler to a url in resources/hedge.edn

<pre>
  <code class="language-clojure" concordion:echo="basicHelloConf()"/>
</pre>

running

<pre>
  <code class="language-bash" concordion:execute="deploy(#TEXT)">
    boot hedge-azure -a hedge-acceptance -r hedge-acceptance-rg
  </code>
</pre>

deploys the hedge application into azure.

after that url [https://hedge-acceptance.azurewebsites.net/api/hello](- "#url") " will answer with

<pre>
  <code class="language-bash" concordion:assert-equals="getresource(#url)">Hello!</code>
</pre>

