// see https://golden-layout.com/tutorials/getting-started.html
var config = JSON.parse(initialGoldenLayout());

var myLayout = new GoldenLayout( config , document.getElementById('container'));
myLayout.registerComponent( 'testComponent', function( container, componentState ){
    var content = renderExample(componentState)
    container.getElement().html( content );
});
myLayout.registerComponent( 'testComponent2', function( container, componentState ){
    var content = renderExample2(componentState)
    container.getElement().html( content );
});
myLayout.registerComponent( 'pushSource', function( container, componentState ){
    var content = renderPushSource(componentState)
    container.getElement().html( content );
});
myLayout.init();

function setupMenu() {
    Menu.initialise(myLayout);
}

function addLayoutChild(newItemConfig) {
  return myLayout.root.contentItems[0].addChild(newItemConfig);
}