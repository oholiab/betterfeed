# betterfeed
> Poorly formatted feed? That's a scrapin'.
## What is it?

A server/library for making RSS feeds work for you.

A fair proportion of RSS feeds these days will do things like truncate the
description field of all items in a feed to force you to view the original page,
or in the case of aggregate feeds, will instead just make your rss feed viewer
do whatever it does for an empty description body.

Betterfeed is a Clojure library and server for providing per-feed and per-domain
rules for repopulating the description field with scrapes of the original
content, using arbitrary user-defined CSS selectors.

The project was born out of frustration with web browsers, ads and content
producers and curators attempting to dictate the way in which their content is
consumed

## Installation

You'll first need to install [rss-utils](https://github.com/oholiab/rss-utils)
and then betterfeed by cloning the repo, `cd`ing into it and doing

    lein install

## Usage

This is pretty basic at the moment.

Crack open a repl and use

```clojure
(require [betterfeed.core :as feed])
(feed/rewrite-feed "https://some.feed/feed.xml" "/tmp/feed.xml") 
```

and then simply point your rss feed reader at the new file. This will attempt to
scrape anything matching `<div class='entry'>` by default, so you'll probably
need to configure it.

## Configuration

Betterfeed utilizes the functional nature of Clojure so that you can easily
construct partial functions at feeds and page contents in order to manipulate
them to your whim.

The best example of this is utilizing the `get-content-method` map to define how
you want to process pages from a given domain before dumping them in the
`<encoded>` field.

For instance, if you only wanted content within the `<div class='entry'>` tag
for domain "some.feed", you would define a `get-content-method` map to match the
domain to use a partial created by the `selector` helper with an
[enlive](http://github.com/cgrand/enlive) CSS selector:

```clojure
(dosync
  (ref-set feed/content-method
    {"some.feed" (feed/selector [:div.entry])}))
```

To utilize more advanced selectors, you may need to include enlive directly to
use some of its helper functions.

For example, if you wanted to match elements with an id attribute matching:

    article-section-1
    article-section-2
    article-section-3
    ...

You could use:

```clojure
(require [net.cgrand.enlive-html :as enl])
(dosync
  (ref-set feed/content-method
    {"some.feed" (feed/selector [(enl/attr-starts :id "article-section-")])}))
```

Addtionally, you can specify feeds which will actually redirect to other sites
for the content so that you can match against the article domains individually:

```clojure
(dosync
  (ref-set feed/will-3xx 
    '(
      "some.feed"
      "some-other.feed"
    )))
```

## Issues
The project is really early days, and I've been using it extensively for my
personal feeds. Any questions or comments or feature requests, just submit an
issue and I'll attempt to address or answer questions.

## License

BSD 3 Clause - see LICENSE
