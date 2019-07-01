// see https://golden-layout.com/tutorials/getting-started.html
var config = JSON.parse(initialGoldenLayout());

var myLayout = new GoldenLayout( config , document.getElementById('container'));
myLayout.registerComponent( 'testComponent', function( container, componentState ){
    var content = renderExample(componentState)
    container.getElement().html( content );
});
myLayout.registerComponent( 'testComponent2', function( container, componentState ){
    var content = renderExample2(componentState)
    //container.props.glEventHub.emit( 'something-happend', {some: 'data' });
    container.getElement().html( content );
});
myLayout.init();


var addMenuItem = function( title, text ) {
    var element = document.createElement('li');
    element.appendChild(document.createTextNode(text));
    var menuContainer = document.getElementById("menuContainer");
    menuContainer.appendChild(element);

   var newItemConfig = {
        title: title,
        type: 'component',
        componentName: 'testComponent',
        componentState: { text: text }
    };

    myLayout.createDragSource( element, newItemConfig );
};

function setupMenu() {

  addMenuItem( 'Add me!', 'You\'ve added me!' );
  addMenuItem( 'Me too!', 'You\'ve added me too!' );

}