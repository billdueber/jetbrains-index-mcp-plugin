class Article < Document
  include Publishable
  include Commentable
end