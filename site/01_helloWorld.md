# Hello World
## July 4, 2019

At this moment I still feel like I'm trying to get a vertical slice completely end-to-end.

This project was born out of the Eyam Sports Association (ESA) website, believe it or not.

I've not really done a webby project in some time, but that's often the kind of thing which is asked of developers.

From there it was apparent that there weren't any really easy/decent (and affordable!) hosting solutions which incorporated back-end compute.
There were some mentions on using AWS lambdas and content served out of S3 buckets or CDNs, but by that point I already started to weave
together disparate components to create some kind of REST service.

At this point I thought I'd just go with a github page for the ESA as static content was 95% of their use-case. So, from this project, I wanted to 
complete filling in the gaps of creating the scaffolding for a REST service which had a UI.

I could've gone w/ the Play framework (or hundreds of others), but as I wanted to support essentially just being able to put a REST endpoint
over-top of some compute, I eventually found [Endpoints](https://gitter.im/julienrf/endpoints), which I'm still pretty happy with.

I also put some code in for generating/using Json Web Tokens, and thought I'd complete the domain by essentially wrapping the 
Monix API with addressable components. This way I could just have all the structure set up for getting a UI on top of the finally interesting bit of programming - the algorithms.

I'm pretty used to a relatively functional approach to programming after writing in scala for over 10 years now, and I see a lot of problems
simply as folding some state over a stream of inputs to produce some outputs.

By having a structure where I can easily mix and match the sources of those inputs with consumers of the outputs, then I can crank out solutions by 
simply bringin in this project.

I also wanted to ensure the REST endpoint part didn't end up being the dumping ground for what grows into a massive project, which is 
often the case.

And so the 'pipelines-rest' part just concerns itself w/ starting up a REST service around some routes, but we can easily add additional
routes and functionality by using composition instead of inheritance. In this way, the user/roles/permissions are added with a specific
mongo dependency downstream from the pipelines-rest component.

Anyway, that's a very raw brain-dump of where I'm at now. Right at this moment things seem to be working out pretty well on the UI
side by just using vanilla scalajs with goldenlayouts, which I discovered after having tried to mix in React -- my conclusion was
that that was overkill and largely obviated by the fact that I already have a decent language generating my javascript for me,
and so don't haven't experienced the problems react portends to solve.