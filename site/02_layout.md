# Front-End Layout Notes

Most of the javascript is done via ScalaJS, and I'll probably move the rest over too ...
I just didn't initially want the overhead of creating a GoldenLayout wrapper over a library
which I wasn't sure was going to pan out.

And so, in addition to the ScalaJS scripts, there is a 'web/https/jslib/app.js' file in the client-xhr project
which contains some hand-cranked javascript used for initialising GoldenLayout.

To add a new component, you would copy an existing 'registerComponent' and expose the
render function via ScalaJS in the GoldenLayoutComponents object.

There's also a 'Constants.components' object which mirrors the keys set up in app.js so that they
can be referred to from ScalaJS. That code-smell will go when we move app.js into ScalaJS space.

# Tables

DataTables looked bulky and cumbersome,
https://clusterize.js.org was easy to just use as a CDN to start with and get started
