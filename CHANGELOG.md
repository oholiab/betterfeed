## Changes between betterfeed 0.1.0 and 0.1.1 (Nov 18th, 2017)
### Bumped to data.xml 0.2.0-alpha3 for xmlns support
* Allows for xmlns:content schema usage
* Included monkey patch for bug in alpha3 causing exceptions when xml or xmlns
  are defined, even when done correctly (e.g. all rss feeds :P)

### Bumped to rss-utils 0.0.10
* It contains some exception bugfixes and the data.xml changes above

### Default repopulation uses content:encoded instead of description
* Currently namespaces will be defined in each tag created by insert-tag rather
  than the rss tag - ugly but okay.
* Made update-body-from-link-contents variadic to allow for this default,
  `field` can also be specified
* Moved "EXTRA SHIT" message so it's true :P
