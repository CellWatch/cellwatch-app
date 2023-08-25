# CellWatch Android App #

This app is a cellular data signal quality measurement tool designed to collect measurement data in
in support of FCC cellular quality challenges.

# Application Architecture #

The app is built on the concepts of [Clean Architecture](https://8thlight.com/blog/uncle-bob/2012/08/13/the-clean-architecture.html),
originally developed by Robert C. Martin a.k.a. "Uncle Bob", which we have adapted to fit our needs.


## What is Clean Architecture? ##

> “The center of your application is not the database. Nor is it one or more of the frameworks you may be using. The center of your application is the use cases of your application" - Uncle Bob

> "A good architecture emphasizes the use-cases and decouples them from peripheral concerns." - Uncle Bob

Clean Architecture incorporates a several key concepts borrowed from other architectural patterns,
but at its core Clean Architecture embodies the principle:

**A good architecture emphasizes what the application does, not how it does it.**

![Original Clean Architecture dependency diagram, by Robert C. Martin](images/UncleBobCleanArchitecture.jpg)

Above is the original Clean Architecture dependency diagram by "Uncle Bob".

And here is our version of the diagram, better reflecting where things live in this project:

![Dependency diagram for our Clean Architecture implementation](images/AndroidCleanArchitecture.png)


### Key Points of Clean Architecture ###

1. **Emphasize the use cases**  
   Clean Architecture is focused first on the Use Cases, or what your application does.
   Each Use Case implements a specific business rule of the application, free from the implementation details
   such as how data is stored or presented.

2. **Package Structure should reflect what the app does**  
   Just as a quick look at an architectural blueprint for a building suggests the building's purpose, looking
   at only the package structure of application built upon Clean Architecture should suggest what the application
   does.  Rather than organizing packages primarily based on layer (e.g. top-level packages such as activity,
   fragment, database, adapter, etc.), packages should be organized by feature (e.g. top level packages such as
   customer, order, catalog, etc.). By looking  at the names of the packages and the names of the Use Cases within
   them, someone should be able to get an idea of what the application does without ever needing to open a file
   and view code.

3. **Dependencies can only point inward**  
   The Use Cases, Entities and other classes of the domain layer that implement the applications business logic
   have no dependencies on and frameworks or external services or APIs. When looking at the concentric circles
   that represent the layers of Clean Architecture, these dependencies can only point inward, i.e. the outer layers
   can know about the inner layers but the inner layers cannot know about the outer layers.  This is known as the
   Dependency Rule.

4. **Different models for different layers**  
   Different layers of the application have different needs of the data models. For example, in the data layer a
   model may need to derive from a base class provided by an ORM library, or include Java annotations to map the
   model to JSON for a RESTful API call. At the presentation layer, a model may need to implement data binding for
   presentation in a view or transform how the data is presented.  And within the domain layer, the Entity model
   should know nothing of these implementation details.  Thus, rather than attempt to overload a single model with
   support for all layers (which often isn't even possible), as a data model moves between the layers map the data
   model to a new data model implemented to support the needs of the specific layer.  If the Entity data model from
   the domain works for an outer layer then feel free to use it, but if the outer layer evolves to require changes
   in the data model, create a new data model to support the outer layer.

5. **Clean Architecture is testable**
   An application that uses Clean Architecture is inherently testable.  This is a benefit from having a domain layer
   that is written in plain Java without external dependencies.

## Examples ##

Examples given here are taken from the
[SafeHarbor Android app-Android: Android app](https://github.gatech.edu/IMTC/SafeHarbor-Android)

## Domain Layer ##

The Domain Layer implements the business requirements of the application, free of implementation details.

Implemented in the `*.domain` subpackage of each feature package.


### Use Case ###

Contains the business logic for a single, specific use case.

Implemented in `*.domain.usecase` packages, extends `UseCase`.

****Responsibilities****
* Execute a single unit of business logic

**Interactions**
* Used by the presentation layer's MVVM ViewModel classes, other Use Cases, and by the background services
  that interact with the panic button.
* Only has dependencies on the domain layer
* All interactions outside of the domain via  interfaces
* Interacts with data via Repository and Gateway interfaces to local storage and external systems
* Interacts with OS services and device hardware via Gateway interfaces.
* slf4j interface for logging (backed by a Tim

**Examples**

**Implementation notes**
* Uses **Flow** to provide an asynchronous interface
* Typically implements a single `execute()` method that may accept arguments and returns a `Flow`
* Plain Kotlin, no Android code


### Entity ###

Represents a business object that concerns the application and is the core models of the domain layer.
Also known as a domain model.

Implemented in `*.domain.entity` packages.

**Responsibilities**:
* Model the relationships with real-world objects / concepts
* Encapsulate the properties required by the application’s business logic

**Interactions**
* May be used directly by all layers of the application
* Likely to be mapped to models specific for other layers of the application such as models from data
  sources or a view model for the presentation layer

**Examples**

**Implementation notes**
* In Uncle Bob's presentations he will mention these models also implement core business rules, but in
  Android applications these are typically [anemic domain models](https://en.wikipedia.org/wiki/Anemic_domain_model)
  with the logic implemented in the Use Cases.
* Plain Java, no Android code


### Gateway ###

Encapsulates a service, system, device hardware or any other component that is external to the domain
and is needed by a Use Case.

Interfaces declared in `*.domain` packages, typically implemented in the `*.gateway` package of the associated
feature.

**Responsibilities**
* Deals with interacting with an external system or component

**Interactions**
* Used by Use Cases
* Returns only *Entities*, classes living in the domain layer, and basic types
* Implementations are free to reference Android APIs and any other dependencies

**Implementation notes**
* Typically expose **RxJava** methods
* Interfaces live in the domain layer, our implementations typically live in the outer implemenation
  layer.  In Uncle Bob's diagram, these appear in his *Interface Adapters* layer-- perhaps because in
  his implementations there is another abstraction between the Gateway or Repository and the implementation
  APIs?


### Repository ###

A specialized gateway that abstracts data access.  In our implementation, these are used to abstract local
data access, while "Gateway" is used for data access on remote systems.  Many other Clean Architecture
implementations use *Repository* interfaces exclusively in place of *Gateways*.

Interfaces declared in `*.domain` packages, typically implemented in the `*.repository` package of the associated
feature.

**Interactions**
* Same as *Gateway*.

**Examples**
* `UserRepository`: Repository for local storage of User properties, implemented by `SharedPreferencesUserRepository`.


## Presentation Layer ##

This project uses an **MVVM (Model-View-View Model)** architecture for the presentation layer.

**MVVM** is quite similar to the **MVP (Model-View-Presenter)** architecture which is quite popular for
Android development.  In MVVM, the *View Model* acts much like an enhanced *Presenter* from MVP in having
the responsibility of implementing the presentation logic.  The *View Model* differs from the *Presenter*,
in that rather than having a reference to the *View*, the *View Model* exposes *Properties* (public data members)
and *Commands* (public methods) to which the *View* binds using [Android's Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).
These bindings typically take the form of data binding expressions written as the value of attribute properties
of view elements in the layout XML of an *Activity* or *Fragment*. One and two way bindings allow changes in the
*View Model* properties to reflect automatically in the *View* and in the case of two-way bindings, user input
to the *View* automatically updates the properties of the *View Model*.


### View Model ###

Implements presentation logic by directing UI changes and handling user input. Exposes *Properties* (public data members)
and *Commands* (public methods) to which the *View* binds using [Android's Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).

Properties are implemented as `public` fields of any type implementing the Data Binding Library's`Observable`
interface.

Commands are implkemented as `public` methods.

Implemented in the `*.presentation` subpackage of the associated feature package, extends `MvvmViewModel`.

**Responsibilities**
* Execute `UseCase` objects to perform business logic
* Direct changes in the *View* by modifying the *View Model*'s public *Property* fields exposed to the *View*
* Direct other UI updates (such showing dialogs or "toasts") through presentation-layer support classes.
* Handle user triggered UI events via public Command methods exposed to the View.
* Trigger UI navigation via a Navigation interface
* Listen to lifecycle events to manage creation and clean up of resources and RxJava subscriptions

**Interactions**
* Does not hold a reference to the *View*, updates to the View occur only through the *View*'s data binding
  to the properties exposed by the *View Model*
* Uses `UseCase` objects to execute business logic
* Defines and uses a navigation interface, implemented by the *View* `Activity`, to navigate the view hierarchy
  and to other screens
* Uses presentation-layer UI support classes such as `DialogPresenter` to show dialog windows or perform other
  supplamental UI operations.
* Should avoid references to Android APIs, with the exception of the Data Binding Library.
* Uses the project's `R` resource class to access resource IDs.  Uses `AppResources` object to get the
  associated resources.

**Implementation notes**
* In our Clean Architecture implementation, the *View Model*  implements only view logic, not business logic.
  All business logic is implemented in *Use Cases*.
* While our implementation does use Android's Data Binding =Library within the View Model, ideally a *View Model*
  should be platform independent.  This is a useful test to apply when determining if code should live in the
  *View* or *View Model*: if the code is an implementation detail for a specific platform or UI control (such as
  setup of the `GoogleMap` and `PlacesAutocompleteTextView` controls in `sitevisit/presentation/SiteVisitActivity.java`)
  it belongs in the *View*.  If the code implements presentation logic that could conceptually be shared across
  both an iOS and Android implementation, it belongs in the *View Model*.
* When using more complex controls (such as `PlacesAutocompleteTextView`) that require method calls to update or
  otherwise do not play well with data binding within the layout XML, the *View* `Activity` may implement the
  data binding in the `Activity` subclass implementation.  (See `sitevisit/presentation/SiteVisitActivity.java` for
  example.)
* For navigation between *Activities*, the *View Model*  defines a navigation interface which is implemented by the
  *View* `Activity`.
* Keep data binding expressions simple.  Logic should live in the *View Model*, not in the View's data binding
  expressions.
* Be aware of the syntactical differences of one-way versus two-way binding expressions:  
  One way: `<TextView android:text="@{user.firstName}" .../>`  
  Two way: `<EditText android:text="@={user.firstName}" .../>`
* The [Data Binding Library's Binding Adapters](https://developer.android.com/reference/android/databinding/BindingAdapter.html)
  can be used to perform conversions or create custom attributes for data binding. For an example of Binding Adapters
  in this project, see `common/presentation/BindingAdapters.java.`
* Don't confuse the `Observable` interface of Android's Data Binding Library and the `Observable` class of
  RxJava.


### View ###

Implements the platform-specific view logic, typically as an Android `Activity`.

Implemented in the `*.presentation` subpackage of the associated feature package, extends `MvvmViewActivity`.

The *View* should be considered part of the outer implementation layer, specific to the Presentation.

**Responsibilities**
* Screen layout
* Animations / transitions
* Data binding of UI control attributes to Properties exposed by the *View Model*
* Propagation of UI events (such as button clicks) to *Command* methods exposed by the *View Model*

**Android-specific responsibilities**
* Use Dagger dependency injection to build the object graph and instantiate the View Model
* Pass Android lifecycle events to the *View Model*
* Implementation of any navigation interface declared by the associated *View Model*, to show other
  another `Activity` or navigate the view hierarchy.


### Model ###

In our Clean Architecture implementation, the **Model** of MVVM is an **Entity** object in the domain layer.
All operations on **Entity** objects should occur through execution of **Use Case** objects.


## Inspiration for this architecture ##

Our interpretation of Clean Architecture borrows from numerous sources, including:

* Original architecture proposed by Robert C. Martin ("Uncle Bob"), documented in many
  articles and talks:
    * The Clean Architecture https://blog.8thlight.com/uncle-bob/2012/08/13/the-clean-architecture.html
    * Robert C. Martin - Clean Architecture https://vimeo.com/43612849
    * Robert C. Martin - Clean Architecture and Design https://www.youtube.com/watch?v=Nsjsiz2A9mg
* Android-CleanArchitecture project and associated blog posts: (https://github.com/android10/Android-CleanArchitecture)
* Chateau - https://github.com/badoo/Chateau
* Rosie - https://github.com/Karumi/Rosie



# Android msak implementation

## Testing with a local server

By default, the app is set up to run against one of M-Lab's servers. If you want to run the server locally, clone and run it:

```
git clone https://github.com/m-lab/msak.git
git checkout sandbox-roberto-server
go build ./cmd/msak-server
./msak-server
```

Then, in `FirstFragment.kt`, uncomment the lines following `// use local server for testing` and comment out the line following `// use real M-Lab server`.

## TODO

- add licensing info for msak (https://github.com/robertodauria/msak)
- add licensing info for AndroidPing (https://github.com/dburckh/AndroidPing).
- add licensing info for m-lab/go (https://github.com/m-lab/go) -- we're copying the memoryless functionality
