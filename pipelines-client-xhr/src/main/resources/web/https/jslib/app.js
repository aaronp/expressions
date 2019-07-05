// see https://golden-layout.com/tutorials/getting-started.html
var config = JSON.parse(initialGoldenLayout());

var myLayout = new GoldenLayout( config , document.getElementById('container'));
myLayout.registerComponent( 'pushSource', function( container, componentState ){
    var content = PushSourceComponent.render(componentState)
    container.getElement().html( content );
});
myLayout.registerComponent( 'sourceTable', function( container, componentState ){
    var content = SourceTableComponent.render(componentState)
    container.getElement().html( content );
});
myLayout.init();

function setupMenu() {
    Menu.initialise(myLayout);
}

// this gets references from GoldenLayoutComponents.addChild.
// TODO - just write this function there
function addLayoutChild(newItemConfig) {
  return myLayout.root.contentItems[0].addChild(newItemConfig);
}

function newClusterize(clusterizeConfig) {
  new Clusterize(clusterizeConfig);
}