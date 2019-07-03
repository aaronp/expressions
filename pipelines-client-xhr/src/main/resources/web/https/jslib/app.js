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


function setupMenu() {


  Menu.addMenuItem( 'Add me!', 'You\'ve added me!' , myLayout);
  Menu.addMenuItem( 'Me too!', 'You\'ve added me too!', myLayout);

}